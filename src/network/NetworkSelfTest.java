package network;

import chess.ChessPosition;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Teste da conexão por socket na própria máquina (localhost).
 * Rodar: java -cp bin network.NetworkSelfTest
 */
public class NetworkSelfTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        scholarsMateOverSocket(freePort());
        quitOverSocket(freePort());
        garbageIsRefused(freePort());
        fastGamesStayInSync();
        wrongServerIsDetected();
        System.out.println("OK: " + passed + " verificações de rede passaram.");
    }

    /** Uma porta livre, para o teste não depender de a 5000 estar desocupada. */
    private static int freePort() throws Exception {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    /** Conecta no servidor, tentando de novo enquanto ele ainda não abriu a porta. */
    private static SocketMessageChannel join(int port) throws Exception {
        java.io.IOException last = null;
        for (int i = 0; i < 50; i++) {
            try {
                return SocketMessageChannel.join("localhost", port);
            } catch (java.io.IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw last;
    }

    /**
     * Vinte partidas completas (o afogamento de 19 lances, com um xeque no meio)
     * jogadas o mais rápido possível, com socket e memória compartilhada.
     * Sem pausa entre os lances, qualquer corrida entre a thread do jogo e a
     * receptora apareceria como #ERRO ou como memória que "não confere".
     */
    private static void fastGamesStayInSync() throws Exception {
        String[] whiteMoves = {"e2e3", "d1h5", "h5a5", "h2h4", "a5c7", "c7d7", "d7b7", "b7b8", "b8c8", "c8e6"};
        String[] blackMoves = {"a7a5", "a8a6", "h7h5", "a6h6", "f7f6", "e8f7", "d8d3", "d3h7", "f7g6"};
        java.nio.file.Path memFile = java.nio.file.Files.createTempFile("xadrez-rede", ".mem");
        boolean allInSync = true;
        boolean allDraws = true;
        boolean memoryAlwaysMatched = true;
        for (int round = 0; round < 20; round++) {
            int port = freePort();
            CompletableFuture<SocketMessageChannel> hostFuture =
                    CompletableFuture.supplyAsync(() -> unchecked(() -> SocketMessageChannel.host(port)));
            SharedGameState whiteMem = SharedGameState.open(memFile);
            whiteMem.reset(new chess.ChessMatch());
            SocketMessageChannel clientChannel = join(port);
            NetworkGame white = new NetworkGame(1, hostFuture.get(5, TimeUnit.SECONDS), whiteMem);
            NetworkGame black = new NetworkGame(2, clientChannel, SharedGameState.open(memFile));
            white.start();
            black.start();
            Thread w = player(white, whiteMoves);
            Thread b = player(black, blackMoves);
            w.join(10000);
            b.join(10000);
            waitUntil(() -> black.lastLog(1).contains("recebido: #EMPATE"), 5000);

            String all = white.lastLog(1000) + " " + black.lastLog(1000);
            allInSync &= !all.contains("#ERRO");
            allDraws &= white.getProtocol().getMatch().getStalemate() && black.getProtocol().getMatch().getStalemate()
                    && black.lastLog(1).contains("recebido: #EMPATE");
            memoryAlwaysMatched &= !all.contains("desligada")
                    && black.getSharedStatus().contains("lance 19 confere");
            white.close();
            black.close();
            if (!(allInSync && allDraws && memoryAlwaysMatched)) {
                break; // já falhou: não precisa jogar as outras
            }
        }
        check(allInSync, "20 partidas rápidas sem nenhum #ERRO");
        check(allDraws, "as 20 partidas terminam em #EMPATE nos dois lados");
        check(memoryAlwaysMatched, "a memória compartilhada confere em todos os lances das 20 partidas");
        try {
            java.nio.file.Files.deleteIfExists(memFile);
        } catch (java.io.IOException e) {
            memFile.toFile().deleteOnExit();
        }
    }

    /** Uma linha fora do protocolo recebe #ERRO e a partida continua. */
    private static void garbageIsRefused(int port) throws Exception {
        CompletableFuture<SocketMessageChannel> hostFuture =
                CompletableFuture.supplyAsync(() -> unchecked(() -> SocketMessageChannel.host(port)));
        java.net.Socket raw = null;
        for (int i = 0; i < 50 && raw == null; i++) {
            try {
                raw = new java.net.Socket("localhost", port);
            } catch (java.io.IOException e) {
                Thread.sleep(100);
            }
        }
        raw.setSoTimeout(5000);
        var in = new java.io.BufferedReader(new java.io.InputStreamReader(raw.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        var out = new java.io.PrintWriter(raw.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);
        NetworkGame white = new NetworkGame(1, hostFuture.get(5, TimeUnit.SECONDS));
        white.start();

        check("#BEMVINDO 2".equals(in.readLine()), "servidor envia #BEMVINDO 2");
        out.println("isso nao e um lance");
        check(in.readLine().startsWith("#ERRO"), "linha fora do protocolo recebe #ERRO");
        out.println("2Pe7e5");
        check(in.readLine().startsWith("#ERRO"), "lance fora da vez recebe #ERRO");
        check(!white.isOver(), "a partida continua depois das mensagens recusadas");

        white.play(pos("e2"), pos("e4"), () -> 'Q');
        check("1Pe2e4".equals(in.readLine()), "o lance das brancas chega pelo socket");
        out.println("2Pe7e5");
        white.waitMyTurn();
        check(white.getProtocol().getVez().isMyTurn() && !white.isOver(), "lance válido é aceito depois dos recusados");

        raw.close(); // conexão cai sem #SAIR
        waitUntil(white::isOver, 5000);
        check(white.getProtocol().isOpponentQuit(), "queda da conexão encerra a partida");
        white.close();
    }

    /** Conectar numa porta onde não está o jogo dá erro claro, sem travar. */
    private static void wrongServerIsDetected() throws Exception {
        try (java.net.ServerSocket other = new java.net.ServerSocket(0)) {
            Thread fake = new Thread(() -> {
                try (java.net.Socket s = other.accept()) {
                    s.getOutputStream().write("HTTP/1.1 400 Bad Request\r\n".getBytes());
                    s.getOutputStream().flush();
                    Thread.sleep(300);
                } catch (Exception ignored) {
                }
            });
            fake.start();
            try {
                SocketMessageChannel.join("localhost", other.getLocalPort());
                check(false, "deveria recusar um servidor que não é o jogo");
            } catch (java.io.IOException e) {
                check(true, "servidor que não é o jogo é recusado: " + e.getMessage());
            }
            fake.join();
        }
    }

    /** Servidor e cliente jogam o mate do pastor, cada um na sua thread. */
    private static void scholarsMateOverSocket(int port) throws Exception {
        CompletableFuture<SocketMessageChannel> hostFuture =
                CompletableFuture.supplyAsync(() -> unchecked(() -> SocketMessageChannel.host(port)));
        SocketMessageChannel clientChannel = join(port);
        SocketMessageChannel hostChannel = hostFuture.get(5, TimeUnit.SECONDS);
        check(true, "cliente recebeu #BEMVINDO 2");

        NetworkGame white = new NetworkGame(1, hostChannel);
        NetworkGame black = new NetworkGame(2, clientChannel);
        white.start();
        black.start();

        Thread w = player(white, "e2e4", "f1c4", "d1h5", "h5f7");
        Thread b = player(black, "e7e5", "b8c6", "g8f6");
        w.join(5000);
        b.join(5000);
        waitUntil(() -> black.lastLog(1).contains("recebido: #MATE 1"), 5000);

        check(white.getProtocol().getMatch().getCheckmate(), "brancas veem o xeque-mate");
        check(black.getProtocol().getMatch().getCheckmate(), "pretas veem o xeque-mate");
        check(black.lastLog(2).contains("recebido: #MATE 1"), "pretas receberam #MATE 1: " + black.lastLog(3));
        white.close();
        black.close();
    }

    /** O cliente desiste no meio da partida e o servidor fica sabendo. */
    private static void quitOverSocket(int port) throws Exception {
        CompletableFuture<SocketMessageChannel> hostFuture =
                CompletableFuture.supplyAsync(() -> unchecked(() -> SocketMessageChannel.host(port)));
        NetworkGame black = new NetworkGame(2, join(port));
        NetworkGame white = new NetworkGame(1, hostFuture.get(5, TimeUnit.SECONDS));
        white.start();
        black.start();

        white.play(pos("e2"), pos("e4"), () -> 'Q');
        black.waitMyTurn();
        black.quit();
        waitUntil(white::isOver, 5000);
        check(white.getProtocol().isOpponentQuit(), "servidor percebe a desistência");
        white.close();
    }

    private static Thread player(NetworkGame game, String... moves) {
        Thread t = new Thread(() -> {
            try {
                for (String mv : moves) {
                    game.waitMyTurn();
                    if (game.isOver()) {
                        return;
                    }
                    game.play(pos(mv.substring(0, 2)), pos(mv.substring(2)), () -> 'Q');
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
    }

    private static ChessPosition pos(String s) {
        return new ChessPosition(s.charAt(0), s.charAt(1) - '0');
    }

    private interface IoSupplier<T> { T get() throws Exception; }

    private static <T> T unchecked(IoSupplier<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("FALHOU: " + what);
        }
        passed++;
    }
}
