package network;

import chess.ChessPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Uma partida de um processo contra o adversário do outro lado do socket.
 *
 * Duas threads trabalham aqui:
 * - a thread do jogo (quem chama play) lê o teclado e envia os lances;
 * - a thread receptora lê o socket o tempo todo e aplica os lances do adversário.
 *
 * As duas mexem no mesmo MatchProtocol e na mesma variável vez. A exclusão
 * mútua vem dos métodos synchronized dessas classes, e a thread do jogo
 * dorme em TurnControl.esperarMinhaVez() até a receptora passar a vez.
 */
public class NetworkGame {

    private final MatchProtocol protocol;
    private final MessageChannel channel;
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private Consumer<String> onEvent = s -> { };
    private Thread receiver;

    // memória compartilhada (sprint 3); null quando não está disponível
    private volatile SharedGameState shared;
    private final AtomicInteger moves = new AtomicInteger();
    private volatile boolean selfQuit;
    private volatile boolean closed;
    private volatile String sharedStatus = "memória compartilhada desligada";

    public NetworkGame(int me, MessageChannel channel) {
        this(me, channel, null);
    }

    public NetworkGame(int me, MessageChannel channel, SharedGameState shared) {
        this.protocol = new MatchProtocol(me);
        this.channel = channel;
        this.shared = shared;
        if (shared != null) {
            sharedStatus = "memória compartilhada: " + shared.getPath();
        }
    }

    /** Chamado a cada mensagem enviada ou recebida (para a tela atualizar). */
    public void setOnEvent(Consumer<String> onEvent) {
        this.onEvent = onEvent;
    }

    /** Inicia a thread que lê o socket. */
    public void start() {
        receiver = new Thread(this::receiveLoop, "receptor-" + protocol.getVez().getMe());
        receiver.setDaemon(true);
        receiver.start();
    }

    private void receiveLoop() {
        try {
            while (true) {
                Message m;
                try {
                    m = channel.receive();
                } catch (ProtocolException e) {
                    // linha fora do protocolo: recusa e continua lendo
                    Message reply = Message.erro(e.getMessage());
                    channel.send(reply);
                    record("enviado: " + reply.toWire());
                    continue;
                }
                if (m == null) {
                    break; // o adversário fechou a conexão
                }
                record("recebido: " + m.toWire());
                List<Message> replies;
                // Região crítica: aplicar o lance, contar e conferir a memória compartilhada
                // acontecem juntos. A thread do jogo acorda assim que a vez passa, mas só
                // consegue jogar (e regravar a memória) depois que esta conferência termina.
                synchronized (protocol) {
                    replies = protocol.receive(m);
                    if (m.isMove() && replies.isEmpty()) {
                        moves.incrementAndGet();
                        checkSharedMemory(m);
                    }
                }
                for (Message reply : replies) {
                    channel.send(reply);
                    record("enviado: " + reply.toWire());
                }
                Message.Type type = m.getType();
                boolean lastMessage = type == Message.Type.SAIR || type == Message.Type.ERRO
                        || ((type == Message.Type.MATE || type == Message.Type.EMPATE) && replies.isEmpty());
                if (lastMessage) {
                    break;
                }
            }
        } catch (IOException e) {
            if (!selfQuit && !closed) {
                record("conexão: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // a conexão acabou sem a partida ter terminado: o adversário foi embora
        if (!protocol.isGameOver() && !selfQuit) {
            protocol.markOpponentGone();
        }
    }

    /** Bloqueia até ser a vez deste jogador ou a partida acabar. */
    public void waitMyTurn() throws InterruptedException {
        protocol.getVez().esperarMinhaVez();
    }

    /** Executa e envia um lance do jogador local. */
    public void play(ChessPosition source, ChessPosition target, Supplier<Character> promotion) throws IOException {
        // Região crítica (mesma trava da thread receptora): jogar, contar, gravar a
        // memória e enviar formam um passo só.
        synchronized (protocol) {
            List<Message> out = protocol.localMove(source, target, promotion);
            moves.incrementAndGet();
            // grava na memória ANTES de enviar: quando o adversário receber o lance,
            // a memória já tem o estado novo para ele conferir
            writeSharedMemory(out.get(0).toWire());
            for (Message m : out) {
                channel.send(m);
                record("enviado: " + m.toWire());
            }
        }
    }

    private void writeSharedMemory(String lastMove) {
        SharedGameState mem = shared;
        if (mem == null) {
            return;
        }
        var match = protocol.getMatch();
        int status = match.getCheckmate() ? SharedGameState.MATE
                : match.getStalemate() ? SharedGameState.EMPATE
                : match.getCheck() ? SharedGameState.XEQUE : SharedGameState.JOGANDO;
        int winner = match.getCheckmate() ? protocol.getVez().getMe() : 0;
        try {
            mem.write(match, protocol.getVez().getVez(), moves.get(), lastMove, status, winner);
            sharedStatus = "memória compartilhada: lance " + moves.get() + " gravado";
        } catch (IOException e) {
            disableShared("erro ao gravar: " + e.getMessage());
        }
    }

    /** Confere se a memória compartilhada mostra o mesmo tabuleiro que este processo calculou. */
    private void checkSharedMemory(Message m) {
        SharedGameState mem = shared;
        if (mem == null) {
            return;
        }
        try {
            SharedGameState.Snapshot snap = mem.read();
            String mine = SharedGameState.encodeBoard(protocol.getMatch());
            if (snap != null && snap.board().equals(mine) && snap.lastMove().equals(m.toWire())
                    && snap.lances() == moves.get()) {
                sharedStatus = "memória compartilhada: lance " + snap.lances() + " confere (versão " + snap.versao() + ")";
            } else {
                disableShared("não confere com o tabuleiro (processos em máquinas diferentes?)");
            }
        } catch (IOException e) {
            disableShared("erro ao ler: " + e.getMessage());
        }
    }

    /** synchronized: a thread do jogo e a receptora podem chegar aqui ao mesmo tempo. */
    private synchronized void disableShared(String reason) {
        SharedGameState mem = shared;
        if (mem == null) {
            return; // a outra thread já desligou
        }
        shared = null;
        sharedStatus = "memória compartilhada desligada: " + reason;
        record(sharedStatus);
        try {
            mem.close();
        } catch (IOException ignored) {
        }
    }

    /** Desiste: avisa o adversário e encerra. */
    public void quit() throws IOException {
        selfQuit = true;
        SharedGameState mem = shared;
        if (mem != null) {
            try {
                mem.write(protocol.getMatch(), protocol.getVez().getVez(), moves.get(),
                        Message.sair(protocol.getVez().getMe()).toWire(),
                        SharedGameState.DESISTENCIA, protocol.getVez().getOpponent());
            } catch (IOException ignored) {
            }
        }
        channel.send(Message.sair(protocol.getVez().getMe()));
        record("enviado: " + Message.sair(protocol.getVez().getMe()).toWire());
        protocol.getVez().encerrar();
        channel.close();
    }

    /** Espera a thread receptora terminar, no máximo ms milissegundos. */
    public void awaitReceiver(long ms) throws InterruptedException {
        if (receiver != null) {
            receiver.join(ms);
        }
    }

    public void close() {
        closed = true;
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }

    private void record(String line) {
        log.add(line);
        onEvent.accept(line);
    }

    public MatchProtocol getProtocol() { return protocol; }
    public String getSharedStatus() { return sharedStatus; }
    public boolean isSelfQuit() { return selfQuit; }
    public boolean isOver() { return protocol.getVez().isEnded(); }

    public List<String> lastLog(int n) {
        synchronized (log) {
            return new ArrayList<>(log.subList(Math.max(0, log.size() - n), log.size()));
        }
    }
}
