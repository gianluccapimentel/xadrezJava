package network;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Testes do protocolo sem bibliotecas externas.
 * Rodar: java -cp bin network.ProtocolSelfTest
 */
public class ProtocolSelfTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        roundTrip();
        rejectsBadMessages();
        turnControl();
        twoProcessesSimulated();
        matchProtocolMate();
        matchProtocolRejects();
        matchProtocolPromotion();
        System.out.println("OK: " + passed + " verificações passaram.");
    }

    private static void roundTrip() {
        String[] samples = {"1Pe2e4", "2Ng8f6", "1Pe7e8=N", "#BEMVINDO 2", "#XEQUE 2", "#MATE 1", "#SAIR 1", "#ERRO A peca escolhida nao e sua"};
        for (String s : samples) {
            check(Message.parse(s).toWire().equals(s), "ida e volta de " + s);
        }
        Message m = Message.parse("2Ng8f6");
        check(m.getPlayer() == 2 && m.getPiece() == 'N', "campos do lance");
        check(m.getSource().toString().equals("g8") && m.getTarget().toString().equals("f6"), "posições do lance");
    }

    private static void rejectsBadMessages() {
        String[] bad = {"", "3Pe2e4", "1Xe2e4", "1Pe2e9", "1Pi2e4", "1Ne7e8=Q", "1Pe7e8=K", "1Pe2", "#FOO 1", "#XEQUE 5"};
        for (String s : bad) {
            try {
                Message.parse(s);
                check(false, "deveria recusar \"" + s + "\"");
            } catch (ProtocolException e) {
                check(true, "recusa \"" + s + "\"");
            }
        }
    }

    private static void turnControl() {
        TurnControl white = new TurnControl(1);
        TurnControl black = new TurnControl(2);
        check(white.isMyTurn() && !black.isMyTurn(), "brancas começam");
        try {
            white.validate(Message.parse("2Pe7e5"));
            check(false, "lance fora da vez deveria ser recusado");
        } catch (ProtocolException e) {
            check(true, "lance fora da vez recusado");
        }
        white.passarVez();
        black.passarVez();
        check(!white.isMyTurn() && black.isMyTurn() && white.getVez() == black.getVez(), "vez igual nos dois processos");
    }

    /** Dois ChessMatch, um para cada "processo", trocando só texto: mate do pastor. */
    private static void twoProcessesSimulated() {
        ChessMatch[] side = {new ChessMatch(), new ChessMatch()};
        TurnControl[] vez = {new TurnControl(1), new TurnControl(2)};
        String[][] game = {{"e2", "e4"}, {"e7", "e5"}, {"f1", "c4"}, {"b8", "c6"}, {"d1", "h5"}, {"g8", "f6"}, {"h5", "f7"}};

        for (int i = 0; i < game.length; i++) {
            int sender = i % 2;      // 0 = jogador 1, 1 = jogador 2
            int receiver = 1 - sender;
            ChessPosition s = pos(game[i][0]);
            ChessPosition t = pos(game[i][1]);

            // quem joga monta a mensagem a partir do próprio tabuleiro
            char piece = pieceAt(side[sender], s);
            Message out = Message.move(vez[sender].getVez(), piece, s, t);
            side[sender].performChessMove(s, t);
            vez[sender].passarVez();
            String wire = out.toWire();

            // quem recebe só vê o texto
            Message in = Message.parse(wire);
            vez[receiver].validate(in);
            check(pieceAt(side[receiver], in.getSource()) == in.getPiece(), "peça confere em " + wire);
            side[receiver].performChessMove(in.getSource(), in.getTarget());
            vez[receiver].passarVez();
        }
        check(side[0].getCheckmate() && side[1].getCheckmate(), "os dois lados detectam o xeque-mate");
        check(Message.mate(1).toWire().equals("#MATE 1"), "mensagem de mate");
    }

    /** Mesmo mate do pastor, agora usando MatchProtocol dos dois lados. */
    private static void matchProtocolMate() {
        MatchProtocol[] side = {new MatchProtocol(1), new MatchProtocol(2)};
        String[] moves = {"e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"};
        List<String> wire = new ArrayList<>();
        for (int i = 0; i < moves.length; i++) {
            int me = i % 2;
            for (Message m : side[me].localMove(pos(moves[i].substring(0, 2)), pos(moves[i].substring(2)), () -> 'Q')) {
                wire.add(m.toWire());
                check(side[1 - me].receive(Message.parse(m.toWire())).isEmpty(), "adversário aceita " + m);
            }
        }
        check(wire.equals(List.of("1Pe2e4", "2Pe7e5", "1Bf1c4", "2Nb8c6", "1Qd1h5", "2Ng8f6", "1Qh5f7", "#MATE 1")),
              "sequência de mensagens do mate: " + wire);
        check(side[0].isGameOver() && side[1].isGameOver(), "partida terminada nos dois lados");
        check(side[0].getCaptured().size() == 1 && side[1].getCaptured().size() == 1, "peças capturadas iguais");
    }

    private static void matchProtocolRejects() {
        MatchProtocol black = new MatchProtocol(2);
        check(!black.receive(Message.parse("1Ne2e4")).isEmpty(), "recusa peça errada");
        check(!black.receive(Message.parse("2Pe7e5")).isEmpty(), "recusa lance do próprio jogador");
        check(!black.receive(Message.parse("1Pe2e5")).isEmpty(), "recusa lance ilegal");
        check(black.getVez().getVez() == 1, "vez não muda após erro");
        check(black.receive(Message.parse("1Pe2e4")).isEmpty(), "aceita lance correto depois do erro");
        check(black.getVez().isMyTurn(), "vez passa para as pretas");
        try {
            new MatchProtocol(2).localMove(pos("e7"), pos("e5"), () -> 'Q');
            check(false, "não deveria jogar fora da vez");
        } catch (ChessException e) {
            check(true, "bloqueia lance local fora da vez");
        }
        MatchProtocol white = new MatchProtocol(1);
        white.receive(Message.sair(2));
        check(white.isOpponentQuit() && white.isGameOver(), "#SAIR encerra a partida");
    }

    /** Peão branco chega em a8 e vira cavalo; o outro lado precisa ver o cavalo. */
    private static void matchProtocolPromotion() {
        MatchProtocol[] side = {new MatchProtocol(1), new MatchProtocol(2)};
        String[] moves = {"a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "b7a8"};
        String last = null;
        for (int i = 0; i < moves.length; i++) {
            int me = i % 2;
            for (Message m : side[me].localMove(pos(moves[i].substring(0, 2)), pos(moves[i].substring(2)), () -> 'N')) {
                last = m.toWire();
                check(side[1 - me].receive(Message.parse(last)).isEmpty(), "adversário aceita " + last);
            }
        }
        check("1Pb7a8=N".equals(last), "mensagem de promoção: " + last);
        for (MatchProtocol p : side) {
            check(p.getMatch().getPieces()[0][0].toString().equals("N"), "cavalo em a8 nos dois tabuleiros");
        }
    }

    private static ChessPosition pos(String s) {
        return new ChessPosition(s.charAt(0), s.charAt(1) - '0');
    }

    private static char pieceAt(ChessMatch match, ChessPosition p) {
        ChessPiece piece = match.getPieces()[8 - p.getRow()][p.getColumn() - 'a'];
        return piece.toString().charAt(0);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("FALHOU: " + what);
        }
        passed++;
    }
}
