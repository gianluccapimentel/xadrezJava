package network;

import chess.ChessMatch;
import chess.ChessPosition;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Testes da memória compartilhada.
 * Rodar: java -cp bin network.SharedMemorySelfTest
 *
 * O teste de exclusão mútua abre um SEGUNDO processo Java. Os dois somam
 * 1 no mesmo contador da memória compartilhada, 200.000 vezes cada. Se a
 * trava falhasse, somas se perderiam e o total ficaria abaixo de 400.000.
 */
public class SharedMemorySelfTest {

    private static final int N = 200_000;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--filho")) {
            child(Path.of(args[1]));
            return;
        }
        Path path = Files.createTempFile("xadrez-teste", ".mem");
        try {
            boardRoundTrip(path);
            twoProcessesCounter(path);
            twoThreadsCounter(path);
        } finally {
            Files.deleteIfExists(path);
        }
        System.out.println("OK: " + passed + " verificações de memória compartilhada passaram.");
    }

    private static void boardRoundTrip(Path path) throws Exception {
        ChessMatch match = new ChessMatch();
        try (SharedGameState a = SharedGameState.open(path); SharedGameState b = SharedGameState.open(path)) {
            a.reset(match);
            check(b.read().board().equals(SharedGameState.encodeBoard(match)), "tabuleiro inicial visível pelo outro mapeamento");
            check(b.read().board().startsWith("rnbqkbnr"), "a8..h8 com as peças pretas");

            match.performChessMove(new ChessPosition('e', 2), new ChessPosition('e', 4));
            a.write(match, 2, 1, "1Pe2e4", SharedGameState.JOGANDO, 0);
            SharedGameState.Snapshot s = b.read();
            check(s.vez() == 2 && s.lances() == 1 && s.lastMove().equals("1Pe2e4"), "vez, lances e último lance");
            check(s.board().charAt(4 * 8 + 4) == 'P', "peão branco em e4 na memória");
            check(s.versao() == 2, "versão incrementada a cada escrita");
        }
    }

    private static void twoProcessesCounter(Path path) throws Exception {
        try (SharedGameState mem = SharedGameState.open(path)) {
            mem.resetCounter();
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    SharedMemorySelfTest.class.getName(), "--filho", path.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            for (int i = 0; i < N; i++) {
                mem.incrementCounter();
            }
            check(child.waitFor() == 0, "processo filho terminou sem erro");
            check(mem.getCounter() == 2L * N, "dois processos: contador = " + mem.getCounter() + " (esperado " + 2 * N + ")");
        }
    }

    private static void child(Path path) throws Exception {
        try (SharedGameState mem = SharedGameState.open(path)) {
            for (int i = 0; i < N; i++) {
                mem.incrementCounter();
            }
        }
    }

    private static void twoThreadsCounter(Path path) throws Exception {
        try (SharedGameState mem = SharedGameState.open(path)) {
            mem.resetCounter();
            Runnable work = () -> {
                try {
                    for (int i = 0; i < N; i++) {
                        mem.incrementCounter();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            Thread t1 = new Thread(work), t2 = new Thread(work);
            t1.start(); t2.start();
            t1.join(); t2.join();
            check(mem.getCounter() == 2L * N, "duas threads: contador = " + mem.getCounter());
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("FALHOU: " + what);
        }
        passed++;
    }
}
