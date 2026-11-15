package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Canal de mensagens sobre um socket TCP: uma mensagem do protocolo por linha.
 *
 * O servidor (jogador 1, brancas) abre a porta e espera o cliente.
 * O cliente (jogador 2, pretas) conecta e recebe "#BEMVINDO 2".
 */
public class SocketMessageChannel implements MessageChannel {

    public static final int DEFAULT_PORT = 5000;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    private SocketMessageChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    /** Lado servidor: espera um cliente na porta e envia #BEMVINDO 2. */
    public static SocketMessageChannel host(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            Socket client = server.accept();
            SocketMessageChannel channel = new SocketMessageChannel(client);
            channel.send(Message.bemVindo(2));
            return channel;
        }
    }

    /** Lado cliente: conecta no servidor e espera o #BEMVINDO. */
    public static SocketMessageChannel join(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            // com tempo limite: um endereço errado não deixa o jogo travado
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            SocketMessageChannel channel = new SocketMessageChannel(socket);
            Message hello = channel.receive();
            if (hello == null || hello.getType() != Message.Type.BEMVINDO || hello.getPlayer() != 2) {
                throw new IOException("O servidor não respondeu com #BEMVINDO 2");
            }
            socket.setSoTimeout(0); // durante a partida, esperar o adversário sem limite
            return channel;
        } catch (SocketTimeoutException e) {
            socket.close();
            throw new IOException("O servidor não respondeu em " + CONNECT_TIMEOUT_MS / 1000 + " segundos");
        } catch (ProtocolException e) {
            socket.close();
            throw new IOException("Quem respondeu nessa porta não é o jogo de xadrez");
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Envia uma mensagem. synchronized porque a thread do jogo (lances) e a
     * thread receptora (respostas #ERRO) podem escrever no socket ao mesmo tempo,
     * e as linhas não podem se misturar.
     */
    @Override
    public synchronized void send(Message m) {
        out.println(m.toWire());
    }

    /**
     * Lê a próxima mensagem. Devolve null quando o outro lado fecha a conexão.
     * Lança ProtocolException se a linha recebida não segue o protocolo; nesse
     * caso a conexão continua válida e a próxima linha pode ser lida normalmente.
     */
    @Override
    public Message receive() throws IOException {
        String line = in.readLine();
        return line == null ? null : Message.parse(line);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public String remoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
