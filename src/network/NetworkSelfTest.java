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
        scholarsMateOverSocket(5051);
        quitOverSocket(5052);
        System.out.println("OK: " + passed + " verificações de rede passaram.");
    }

    /** Servidor e cliente jogam o mate do pastor, cada um na sua thread. */
    private static void scholarsMateOverSocket(int port) throws Exception {
        CompletableFuture<SocketMessageChannel> hostFuture =
                CompletableFuture.supplyAsync(() -> unchecked(() -> SocketMessageChannel.host(port)));
        Thread.sleep(200);
        SocketMessageChannel clientChannel = SocketMessageChannel.join("localhost", port);
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
        Thread.sleep(200);
        NetworkGame black = new NetworkGame(2, SocketMessageChannel.join("localhost", port));
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
