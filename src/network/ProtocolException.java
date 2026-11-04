package network;

/** Lançada quando uma mensagem não segue o protocolo. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }
}
