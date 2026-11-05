package network;

import java.io.IOException;

/**
 * Canal por onde os dois jogadores trocam mensagens do protocolo.
 *
 * O jogo só conhece esta interface. Hoje existe o LocalChannel (os dois
 * jogadores no mesmo processo). No sprint 2 entra o canal por socket, e o
 * resto do jogo não precisa mudar.
 */
public interface MessageChannel extends AutoCloseable {

    void send(Message m) throws IOException;

    /** Bloqueia até chegar a próxima mensagem. */
    Message receive() throws IOException, InterruptedException;

    @Override
    void close() throws IOException;
}
