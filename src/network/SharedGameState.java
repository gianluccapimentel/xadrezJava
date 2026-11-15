package network;

import chess.ChessMatch;
import chess.ChessPiece;
import chess.Color;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Memória compartilhada entre os processos da partida.
 *
 * Um arquivo de 128 bytes é mapeado na memória de cada processo com
 * FileChannel.map (MappedByteBuffer). O que um processo escreve no buffer
 * o outro enxerga direto na memória dele, sem passar pelo socket.
 *
 * Exclusão mútua em dois níveis:
 * - entre PROCESSOS: FileLock exclusivo sobre o arquivo;
 * - entre THREADS do mesmo processo: ReentrantLock (o FileLock pertence à JVM
 *   inteira e não pode ser pego duas vezes pelo mesmo processo).
 *
 * Layout (offset em bytes):
 *   0  int  MAGIC (0x58414452 = "XADR")
 *   4  int  vez (1 ou 2)
 *   8  int  número de lances jogados
 *  12  int  status (0 jogando, 1 xeque, 2 xeque-mate, 3 desistência, 4 empate)
 *  16  int  vencedor (0 nenhum, 1 ou 2)
 *  20  int  tamanho do último lance
 *  24  16B  último lance no formato do protocolo (ex.: "1Pe2e4")
 *  40  64B  tabuleiro, uma letra por casa de a8 a h1 (maiúscula = brancas,
 *           minúscula = pretas, '.' = vazia)
 * 104  long versão (incrementa a cada escrita)
 * 112  long contador usado nos testes de exclusão mútua
 */
public class SharedGameState implements AutoCloseable {

    public static final int SIZE = 128;
    public static final int MAGIC = 0x58414452;

    public static final int JOGANDO = 0, XEQUE = 1, MATE = 2, DESISTENCIA = 3, EMPATE = 4;

    private static final int OFF_MAGIC = 0, OFF_VEZ = 4, OFF_LANCES = 8, OFF_STATUS = 12,
            OFF_VENCEDOR = 16, OFF_LAST_LEN = 20, OFF_LAST = 24, OFF_BOARD = 40,
            OFF_VERSAO = 104, OFF_CONTADOR = 112;

    private static final ReentrantLock threadLock = new ReentrantLock();

    private final Path path;
    private final RandomAccessFile file;
    private final FileChannel channel;
    private final MappedByteBuffer mem;

    private SharedGameState(Path path) throws IOException {
        this.path = path;
        this.file = new RandomAccessFile(path.toFile(), "rw");
        this.channel = file.getChannel();
        this.mem = channel.map(FileChannel.MapMode.READ_WRITE, 0, SIZE);
    }

    /** Arquivo padrão da partida, na pasta temporária, um por porta. */
    public static Path defaultPath(int port) {
        return Path.of(System.getProperty("java.io.tmpdir"), "xadrez-" + port + ".mem");
    }

    /** Abre (ou cria) a memória compartilhada. */
    public static SharedGameState open(Path path) throws IOException {
        return new SharedGameState(path);
    }

    /** Servidor: zera a memória e grava o tabuleiro inicial. */
    public void reset(ChessMatch match) throws IOException {
        withLock(() -> {
            for (int i = 0; i < SIZE; i++) {
                mem.put(i, (byte) 0);
            }
            mem.putInt(OFF_MAGIC, MAGIC);
            writeUnlocked(match, 1, 0, null, JOGANDO, 0);
            return null;
        });
    }

    /** Grava o estado depois de um lance. Quem fez o lance é quem grava. */
    public void write(ChessMatch match, int vez, int lances, String lastMove, int status, int vencedor) throws IOException {
        withLock(() -> {
            writeUnlocked(match, vez, lances, lastMove, status, vencedor);
            return null;
        });
    }

    private void writeUnlocked(ChessMatch match, int vez, int lances, String lastMove, int status, int vencedor) {
        mem.putInt(OFF_VEZ, vez);
        mem.putInt(OFF_LANCES, lances);
        mem.putInt(OFF_STATUS, status);
        mem.putInt(OFF_VENCEDOR, vencedor);
        byte[] last = lastMove == null ? new byte[0] : lastMove.getBytes(StandardCharsets.US_ASCII);
        mem.putInt(OFF_LAST_LEN, last.length);
        for (int i = 0; i < 16; i++) {
            mem.put(OFF_LAST + i, i < last.length ? last[i] : 0);
        }
        String board = encodeBoard(match);
        for (int i = 0; i < 64; i++) {
            mem.put(OFF_BOARD + i, (byte) board.charAt(i));
        }
        mem.putLong(OFF_VERSAO, mem.getLong(OFF_VERSAO) + 1);
        // Não é preciso chamar force(): os processos mapeiam as MESMAS páginas de
        // memória, então a escrita já está visível para o outro. force() só
        // serviria para gravar no disco, o que a partida não precisa.
    }

    /** Lê uma cópia consistente do estado (nunca pega uma escrita pela metade). */
    public Snapshot read() throws IOException {
        return withLock(() -> {
            if (mem.getInt(OFF_MAGIC) != MAGIC) {
                return null; // partida ainda não começou
            }
            int len = Math.min(16, Math.max(0, mem.getInt(OFF_LAST_LEN)));
            byte[] last = new byte[len];
            for (int i = 0; i < len; i++) {
                last[i] = mem.get(OFF_LAST + i);
            }
            byte[] board = new byte[64];
            for (int i = 0; i < 64; i++) {
                board[i] = mem.get(OFF_BOARD + i);
            }
            return new Snapshot(mem.getInt(OFF_VEZ), mem.getInt(OFF_LANCES), mem.getInt(OFF_STATUS),
                    mem.getInt(OFF_VENCEDOR), new String(last, StandardCharsets.US_ASCII),
                    new String(board, StandardCharsets.US_ASCII), mem.getLong(OFF_VERSAO));
        });
    }

    /** Soma 1 ao contador de teste, com exclusão mútua. */
    public long incrementCounter() throws IOException {
        return withLock(() -> {
            long v = mem.getLong(OFF_CONTADOR) + 1;
            Thread.yield(); // aumenta a chance de corrida se a trava não funcionasse
            mem.putLong(OFF_CONTADOR, v);
            return v;
        });
    }

    public long getCounter() throws IOException {
        return withLock(() -> mem.getLong(OFF_CONTADOR));
    }

    public void resetCounter() throws IOException {
        withLock(() -> { mem.putLong(OFF_CONTADOR, 0); return null; });
    }

    // ---------- exclusão mútua ----------

    private interface Section<T> { T run(); }

    /** Região crítica: primeiro a trava entre threads, depois a trava entre processos. */
    private <T> T withLock(Section<T> section) throws IOException {
        threadLock.lock();
        try (FileLock lock = channel.lock(0, SIZE, false)) {
            return section.run();
        } finally {
            threadLock.unlock();
        }
    }

    // ---------- tabuleiro <-> texto ----------

    /** 64 letras, de a8 a h1. */
    public static String encodeBoard(ChessMatch match) {
        StringBuilder sb = new StringBuilder(64);
        ChessPiece[][] pieces = match.getPieces();
        for (ChessPiece[] row : pieces) {
            for (ChessPiece p : row) {
                if (p == null) {
                    sb.append('.');
                } else {
                    char c = p.toString().charAt(0);
                    sb.append(p.getColor() == Color.WHITE ? c : Character.toLowerCase(c));
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        channel.close();
        file.close();
    }

    public Path getPath() {
        return path;
    }

    /** Cópia do que estava na memória num instante. */
    public record Snapshot(int vez, int lances, int status, int vencedor, String lastMove, String board, long versao) {

        /** A partida registrada na memória já acabou? */
        public boolean finished() {
            return status == MATE || status == DESISTENCIA || status == EMPATE;
        }

        public String statusText() {
            return switch (status) {
                case XEQUE -> "xeque";
                case MATE -> "xeque-mate, vencedor: jogador " + vencedor;
                case DESISTENCIA -> "desistência, vencedor: jogador " + vencedor;
                case EMPATE -> "empate por afogamento";
                default -> "jogando";
            };
        }
    }
}
