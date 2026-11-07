package network;

import chess.ChessPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public NetworkGame(int me, MessageChannel channel) {
        this.protocol = new MatchProtocol(me);
        this.channel = channel;
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
                for (Message reply : protocol.receive(m)) {
                    channel.send(reply);
                    record("enviado: " + reply.toWire());
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
        for (Message m : protocol.localMove(source, target, promotion)) {
            channel.send(m);
            record("enviado: " + m.toWire());
        }
    }

    /** Desiste: avisa o adversário e encerra. */
    public void quit() throws IOException {
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
    public boolean isOver() { return protocol.getVez().isEnded(); }

    public List<String> lastLog(int n) {
        synchronized (log) {
            return new ArrayList<>(log.subList(Math.max(0, log.size() - n), log.size()));
        }
    }
}
