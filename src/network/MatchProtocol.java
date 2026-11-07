package network;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Liga o protocolo ao jogo. Cada processo tem um MatchProtocol com o seu
 * próprio ChessMatch e a sua própria variável vez (TurnControl).
 *
 * - localMove: o jogador deste processo jogou. Executa o lance e devolve
 *   as mensagens a enviar ao adversário (o lance e, se for o caso, #XEQUE ou #MATE).
 * - receive: chegou uma mensagem do adversário. Valida, aplica no tabuleiro
 *   e devolve as respostas (#ERRO se o lance for recusado).
 */
public class MatchProtocol {

    private final ChessMatch match;
    private final TurnControl vez;
    private final List<ChessPiece> captured = new ArrayList<>();
    private boolean opponentQuit;
    private String lastError;

    public MatchProtocol(int me) {
        this(new ChessMatch(), new TurnControl(me));
    }

    public MatchProtocol(ChessMatch match, TurnControl vez) {
        this.match = match;
        this.vez = vez;
    }

    /**
     * Executa um lance do jogador local.
     * @param promotionChooser chamado só se houver promoção; devolve Q, R, B ou N
     * @return mensagens a enviar ao adversário, na ordem
     */
    public synchronized List<Message> localMove(ChessPosition source, ChessPosition target,
                                                Supplier<Character> promotionChooser) {
        if (isGameOver()) {
            throw new ChessException("A partida já terminou");
        }
        if (!vez.isMyTurn()) {
            throw new ChessException("Aguarde: é a vez do jogador " + vez.getVez());
        }
        char piece = pieceAt(source);
        ChessPiece capturedPiece = match.performChessMove(source, target); // lança ChessException se ilegal
        if (capturedPiece != null) {
            captured.add(capturedPiece);
        }

        Character promotion = null;
        if (match.getPromoted() != null) {
            promotion = Character.toUpperCase(promotionChooser.get());
            match.replacePromotedPiece(String.valueOf(promotion));
        }

        List<Message> out = new ArrayList<>();
        out.add(Message.move(vez.getMe(), piece, source, target, promotion));
        out.addAll(statusMessages(vez.getMe()));
        vez.passarVez();
        if (match.getCheckmate()) {
            vez.encerrar();
        }
        return out;
    }

    /**
     * Processa uma mensagem recebida do adversário.
     * @return respostas a enviar de volta (vazia quando está tudo certo)
     */
    public synchronized List<Message> receive(Message m) {
        List<Message> replies = new ArrayList<>();
        switch (m.getType()) {
            case MOVE -> {
                try {
                    applyOpponentMove(m);
                } catch (ProtocolException | ChessException e) {
                    replies.add(Message.erro(e.getMessage()));
                }
            }
            case XEQUE -> {
                if (!match.getCheck() || m.getPlayer() != vez.getMe()) {
                    replies.add(Message.erro("Estado divergente: " + m.toWire() + " não confere com o tabuleiro"));
                }
            }
            case MATE -> {
                if (!match.getCheckmate()) {
                    replies.add(Message.erro("Estado divergente: " + m.toWire() + " não confere com o tabuleiro"));
                }
            }
            case SAIR -> markOpponentGone();
            case ERRO -> lastError = m.getText();
            case BEMVINDO -> { /* tratado na conexão (sprint 2) */ }
        }
        return replies;
    }

    /** O adversário saiu ou a conexão caiu: a partida acaba. */
    public synchronized void markOpponentGone() {
        opponentQuit = true;
        vez.encerrar();
    }

    private void applyOpponentMove(Message m) {
        if (m.getPlayer() != vez.getOpponent()) {
            throw new ProtocolException("Lance recebido do jogador " + m.getPlayer() + ", esperado " + vez.getOpponent());
        }
        vez.validate(m);
        char real = pieceAt(m.getSource());
        if (real != m.getPiece()) {
            throw new ProtocolException("Peça em " + m.getSource() + " é " + real + ", não " + m.getPiece());
        }
        ChessPiece capturedPiece = match.performChessMove(m.getSource(), m.getTarget());
        if (capturedPiece != null) {
            captured.add(capturedPiece);
        }
        if (match.getPromoted() != null && m.getPromotion() != null) {
            match.replacePromotedPiece(String.valueOf(m.getPromotion()));
        }
        vez.passarVez();
        if (match.getCheckmate()) {
            vez.encerrar();
        }
    }

    private List<Message> statusMessages(int mover) {
        List<Message> msgs = new ArrayList<>();
        if (match.getCheckmate()) {
            msgs.add(Message.mate(mover));
        } else if (match.getCheck()) {
            msgs.add(Message.xeque(mover == 1 ? 2 : 1));
        }
        return msgs;
    }

    private char pieceAt(ChessPosition p) {
        ChessPiece piece = match.getPieces()[8 - p.getRow()][p.getColumn() - 'a'];
        if (piece == null) {
            throw new ChessException("Não existe peça em " + p);
        }
        return piece.toString().charAt(0);
    }

    // ---------- consultas ----------

    public ChessMatch getMatch() { return match; }
    public TurnControl getVez() { return vez; }
    public List<ChessPiece> getCaptured() { return captured; }
    public boolean isOpponentQuit() { return opponentQuit; }
    public boolean isGameOver() { return match.getCheckmate() || opponentQuit; }

    /** Último #ERRO recebido do adversário, ou null. Lê e limpa. */
    public synchronized String takeLastError() {
        String e = lastError;
        lastError = null;
        return e;
    }
}
