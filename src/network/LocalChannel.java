package network;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Par de canais em memória: o que um lado envia, o outro recebe.
 * As mensagens passam como texto (toWire/parse), exatamente como vão
 * passar pelo socket, para o protocolo ser testado de verdade.
 */
public class LocalChannel implements MessageChannel {

    private final BlockingQueue<String> in;
    private final BlockingQueue<String> out;
    private final String name;

    private LocalChannel(String name, BlockingQueue<String> in, BlockingQueue<String> out) {
        this.name = name;
        this.in = in;
        this.out = out;
    }

    /** Cria os dois pontos do canal: [0] para o jogador 1, [1] para o jogador 2. */
    public static LocalChannel[] pair() {
        BlockingQueue<String> aToB = new LinkedBlockingQueue<>();
        BlockingQueue<String> bToA = new LinkedBlockingQueue<>();
        return new LocalChannel[] {
            new LocalChannel("jogador 1", bToA, aToB),
            new LocalChannel("jogador 2", aToB, bToA)
        };
    }

    @Override
    public void send(Message m) {
        out.add(m.toWire());
    }

    @Override
    public Message receive() throws InterruptedException {
        return Message.parse(in.take());
    }

    /** Há mensagem esperando para ser lida? */
    public boolean hasPending() {
        return !in.isEmpty();
    }

    @Override
    public void close() { }

    @Override
    public String toString() {
        return "LocalChannel(" + name + ")";
    }
}
