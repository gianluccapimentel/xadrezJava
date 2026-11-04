package network;

/**
 * A variável "vez" do enunciado. Cada processo tem a sua cópia,
 * e as duas ficam iguais porque só mudam quando um lance é enviado ou recebido.
 *
 * vez == 1 -> joga o jogador 1 (brancas)
 * vez == 2 -> joga o jogador 2 (pretas)
 *
 * Os métodos são synchronized porque, a partir do sprint 2, a thread que
 * recebe mensagens do socket e a thread do jogo acessam este objeto ao mesmo tempo.
 */
public class TurnControl {

    private final int me;  // 1 se este processo é o servidor (brancas), 2 se é o cliente (pretas)
    private int vez = 1;   // as brancas começam

    public TurnControl(int me) {
        if (me != 1 && me != 2) {
            throw new IllegalArgumentException("Jogador deve ser 1 ou 2");
        }
        this.me = me;
    }

    public synchronized int getVez() {
        return vez;
    }

    public int getMe() {
        return me;
    }

    public int getOpponent() {
        return me == 1 ? 2 : 1;
    }

    public synchronized boolean isMyTurn() {
        return vez == me;
    }

    /** Confere se um lance pertence a quem está com a vez. Não altera a vez. */
    public synchronized void validate(Message m) {
        if (!m.isMove()) {
            return;
        }
        if (m.getPlayer() != vez) {
            throw new ProtocolException("Não é a vez do jogador " + m.getPlayer() + " (vez = " + vez + ")");
        }
    }

    /** Passa a vez para o outro jogador, depois de um lance aceito. */
    public synchronized void passarVez() {
        vez = (vez == 1) ? 2 : 1;
        notifyAll(); // acorda quem estiver esperando a vez (usado no sprint 2)
    }

    /** Bloqueia a thread atual até chegar a vez deste processo. */
    public synchronized void esperarMinhaVez() throws InterruptedException {
        while (vez != me) {
            wait();
        }
    }
}
