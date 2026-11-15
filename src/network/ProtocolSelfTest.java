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
        matchProtocolStalemate();
        matchProtocolPromotionCheck();
        matchProtocolDesync();
        checkNoticeAfterReply();
        matchProtocolPromotion();
        System.out.println("OK: " + passed + " verificações passaram.");
    }

    private static void roundTrip() {
        String[] samples = {"1Pe2e4", "2Ng8f6", "1Pe7e8=N", "#BEMVINDO 2", "#XEQUE 2", "#MATE 1", "#EMPATE", "#SAIR 1", "#ERRO A peca escolhida nao e sua"};
        for (String s : samples) {
            check(Message.parse(s).toWire().equals(s), "ida e volta de " + s);
        }
        Message m = Message.parse("2Ng8f6");
        check(m.getPlayer() == 2 && m.getPiece() == 'N', "campos do lance");
        check(m.getSource().toString().equals("g8") && m.getTarget().toString().equals("f6"), "posições do lance");
    }

    private static void rejectsBadMessages() {
        String[] bad = {"", "3Pe2e4", "1Xe2e4", "1Pe2e9", "1Pi2e4", "1Ne7e8=Q", "1Pe7e8=K", "1Pe2", "#FOO 1", "#XEQUE 5", "#EMPATE 1", "#MATE"};
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

    /** Afogamento (Sam Loyd, 10 lances): os dois lados encerram com #EMPATE. */
    private static void matchProtocolStalemate() {
        MatchProtocol[] side = {new MatchProtocol(1), new MatchProtocol(2)};
        String[] moves = {"e2e3", "a7a5", "d1h5", "a8a6", "h5a5", "h7h5", "h2h4", "a6h6", "a5c7", "f7f6",
                          "c7d7", "e8f7", "d7b7", "d8d3", "b7b8", "d3h7", "b8c8", "f7g6", "c8e6"};
        List<String> wire = exchange(side, moves, 'Q');
        check(wire.subList(wire.size() - 2, wire.size()).equals(List.of("1Qc8e6", "#EMPATE")), "afogamento envia #EMPATE: " + wire);
        check(wire.contains("#XEQUE 2"), "xeque no meio da partida envia #XEQUE 2");
        check(side[0].isGameOver() && side[1].isGameOver(), "afogamento encerra a partida nos dois lados");
        check(side[0].getVez().isEnded() && side[1].getVez().isEnded(), "ninguém fica esperando a vez depois do empate");
        check(!side[1].receive(Message.mate(1)).isEmpty(), "#MATE falso é recusado");
    }

    /** O xeque anunciado depende da peça escolhida na promoção. */
    private static void matchProtocolPromotionCheck() {
        String[] moves = {"a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "h2h3", "e7e6", "g2g3", "d8f6", "b7a8"};

        MatchProtocol[] knight = {new MatchProtocol(1), new MatchProtocol(2)};
        List<String> wire = exchange(knight, moves, 'N');
        check(wire.get(wire.size() - 1).equals("1Pb7a8=N"), "promoção a cavalo não anuncia xeque: " + wire.subList(wire.size() - 2, wire.size()));
        check(!knight[1].getMatch().getCheck(), "quem recebe o cavalo também não vê xeque");

        MatchProtocol[] rook = {new MatchProtocol(1), new MatchProtocol(2)};
        wire = exchange(rook, moves, 'R');
        check(wire.subList(wire.size() - 2, wire.size()).equals(List.of("1Pb7a8=R", "#XEQUE 2")), "promoção a torre anuncia #XEQUE 2");
        check(rook[1].getMatch().getCheck(), "quem recebe a torre vê o xeque");

        MatchProtocol black = new MatchProtocol(2);
        check(!black.receive(Message.parse("1Pe2e4=Q")).isEmpty(), "recusa promoção fora da última fileira");
        check(black.getVez().getVez() == 1, "vez não muda depois da promoção recusada");
    }

    /** Se o adversário responde #ERRO, os tabuleiros divergiram: a partida acaba em vez de travar. */
    private static void matchProtocolDesync() {
        MatchProtocol white = new MatchProtocol(1);
        white.localMove(pos("e2"), pos("e4"), () -> 'Q');
        check(!white.isGameOver(), "partida segue normalmente");
        white.receive(Message.erro("Peça em e2 é P, não N"));
        check(white.isDesynced() && white.isGameOver(), "#ERRO recebido encerra a partida");
        check(white.getVez().isEnded(), "#ERRO recebido acorda quem esperava a vez");
        check("Peça em e2 é P, não N".equals(white.takeLastError()), "motivo do #ERRO fica guardado");
    }

    /**
     * O #XEQUE pode ser lido depois que o jogador já respondeu ao xeque, porque a
     * vez passa assim que o lance é aplicado. Ele tem que ser aceito mesmo assim.
     */
    private static void checkNoticeAfterReply() {
        MatchProtocol white = new MatchProtocol(1);
        MatchProtocol black = new MatchProtocol(2);
        String[] moves = {"e2e4", "f7f6", "d2d4", "e7e6"};
        for (int i = 0; i < moves.length; i++) {
            MatchProtocol mover = (i % 2 == 0) ? white : black;
            MatchProtocol other = (i % 2 == 0) ? black : white;
            for (Message m : mover.localMove(pos(moves[i].substring(0, 2)), pos(moves[i].substring(2)), () -> 'Q')) {
                other.receive(Message.parse(m.toWire()));
            }
        }
        List<Message> out = white.localMove(pos("d1"), pos("h5"), () -> 'Q');
        check(out.size() == 2 && out.get(1).toWire().equals("#XEQUE 2"), "Dh5 dá xeque: " + out);
        check(black.receive(Message.parse(out.get(0).toWire())).isEmpty(), "pretas aceitam o lance que dá xeque");
        black.localMove(pos("e8"), pos("e7"), () -> 'Q'); // respondem ANTES de ler o #XEQUE
        check(black.receive(Message.parse("#XEQUE 2")).isEmpty(), "#XEQUE lido depois da resposta ainda é aceito");
        check(!black.receive(Message.parse("#XEQUE 1")).isEmpty(), "#XEQUE para o jogador errado é recusado");
    }

    /** Joga a sequência alternando os lados e devolve tudo o que passou pelo "fio". */
    private static List<String> exchange(MatchProtocol[] side, String[] moves, char promotion) {
        List<String> wire = new ArrayList<>();
        for (int i = 0; i < moves.length; i++) {
            int me = i % 2;
            for (Message m : side[me].localMove(pos(moves[i].substring(0, 2)), pos(moves[i].substring(2)), () -> promotion)) {
                wire.add(m.toWire());
                check(side[1 - me].receive(Message.parse(m.toWire())).isEmpty(), "adversário aceita " + m);
            }
        }
        return wire;
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
