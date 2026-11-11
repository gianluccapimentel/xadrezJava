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
            Message m;
            while ((m = channel.receive()) != null) {
                record("recebido: " + m.toWire());
                List<Message> replies = protocol.receive(m);
                for (Message reply : replies) {
                    channel.send(reply);
                    record("enviado: " + reply.toWire());
                }
                if (m.isMove() && replies.isEmpty()) {
                    moves.incrementAndGet();
                    checkSharedMemory(m);
                }
                if (m.getType() == Message.Type.SAIR) {
                    break;
                }
            }
        } catch (IOException | ProtocolException e) {
            record("conexão: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!protocol.getMatch().getCheckmate()) {
            protocol.markOpponentGone();
        }
    }

    /** Bloqueia até ser a vez deste jogador ou a partida acabar. */
    public void waitMyTurn() throws InterruptedException {
        protocol.getVez().esperarMinhaVez();
    }

    /** Executa e envia um lance do jogador local. */
    public void play(ChessPosition source, ChessPosition target, Supplier<Character> promotion) throws IOException {
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

    private void writeSharedMemory(String lastMove) {
        if (shared == null) {
            return;
        }
        var match = protocol.getMatch();
        int status = match.getCheckmate() ? SharedGameState.MATE
                : match.getCheck() ? SharedGameState.XEQUE : SharedGameState.JOGANDO;
        int winner = match.getCheckmate() ? protocol.getVez().getMe() : 0;
        try {
            shared.write(match, protocol.getVez().getVez(), moves.get(), lastMove, status, winner);
            sharedStatus = "memória compartilhada: lance " + moves.get() + " gravado";
        } catch (IOException e) {
            disableShared("erro ao gravar: " + e.getMessage());
        }
    }

    /** Confere se a memória compartilhada mostra o mesmo tabuleiro que este processo calculou. */
    private void checkSharedMemory(Message m) {
        if (shared == null) {
            return;
        }
        try {
            SharedGameState.Snapshot snap = shared.read();
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

    private void disableShared(String reason) {
        sharedStatus = "memória compartilhada desligada: " + reason;
        record(sharedStatus);
        try {
            shared.close();
        } catch (IOException ignored) {
        }
        shared = null;
    }

    /** Desiste: avisa o adversário e encerra. */
    public void quit() throws IOException {
        if (shared != null) {
            try {
                shared.write(protocol.getMatch(), protocol.getVez().getVez(), moves.get(),
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

    public void close() {
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
    public boolean isOver() { return protocol.getVez().isEnded(); }

    public List<String> lastLog(int n) {
        synchronized (log) {
            return new ArrayList<>(log.subList(Math.max(0, log.size() - n), log.size()));
        }
    }
}
