package chess;

import boardGame.Board;
import boardGame.Piece;
import boardGame.Position;
import chess.pieces.Bishop;
import chess.pieces.King;
import chess.pieces.Knight;
import chess.pieces.Pawn;
import chess.pieces.Queen;
import chess.pieces.Rook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Testes das regras do xadrez.
 * Rodar: java -cp bin chess.ChessRulesSelfTest
 *
 * A parte principal é o "perft": contar quantas sequências de lances legais
 * existem a partir de uma posição e comparar com os números conhecidos da
 * literatura de programação de xadrez. Se qualquer regra estiver errada
 * (roque, en passant, promoção, cravada, xeque), a contagem não fecha.
 *
 * Para montar posições no meio do jogo, o teste usa reflexão para colocar as
 * peças direto no tabuleiro. O código do jogo não precisa disso.
 */
public class ChessRulesSelfTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        perft();
        castling();
        promotionRecalculatesCheck();
        enPassantCanAnswerCheck();
        stalemate();
        foolsMate();
        System.out.println("OK: " + passed + " verificações das regras do xadrez passaram.");
    }

    // ---------- perft ----------

    private static void perft() throws Exception {
        Object[][] cases = {
            {"posição inicial", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", new long[]{20, 400, 8902}},
            {"roques e cravadas", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -", new long[]{48, 2039}},
            {"en passant e final", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -", new long[]{14, 191, 2812}},
            {"promoções (brancas)", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq -", new long[]{6, 264}},
            {"promoções (pretas)", "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ -", new long[]{6, 264}},
            {"meio-jogo com xeques", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ -", new long[]{44, 1486}},
            {"meio-jogo simétrico", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - -", new long[]{46, 2079}},
        };
        for (Object[] c : cases) {
            long[] expected = (long[]) c[2];
            for (int depth = 1; depth <= expected.length; depth++) {
                long got = countMoves((String) c[1], new ArrayList<>(), depth);
                check(got == expected[depth - 1],
                        "perft " + c[0] + ", profundidade " + depth + ": " + got + " (esperado " + expected[depth - 1] + ")");
            }
        }
    }

    /** Conta as sequências de lances legais com o tamanho pedido. */
    private static long countMoves(String fen, List<int[]> path, int depth) throws Exception {
        ChessMatch match = position(fen, path);
        if (match.isGameOver()) {
            return 0;
        }
        Color side = match.getCurrentPlayer();
        ChessPiece[][] pieces = match.getPieces();
        List<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (pieces[r][c] == null || pieces[r][c].getColor() != side) {
                    continue;
                }
                boolean[][] mat = pieces[r][c].possibleMoves();
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        if (mat[i][j]) {
                            candidates.add(new int[]{r, c, i, j, 0});
                        }
                    }
                }
            }
        }
        long count = 0;
        for (int[] mv : candidates) {
            ChessMatch trial = position(fen, path);
            try {
                trial.performChessMove(square(mv[0], mv[1]), square(mv[2], mv[3]));
            } catch (ChessException e) {
                continue; // deixaria o próprio rei em xeque
            }
            char[] choices = trial.getPromoted() != null ? new char[]{'Q', 'R', 'B', 'N'} : new char[]{0};
            if (depth == 1) {
                count += choices.length;
                continue;
            }
            for (char choice : choices) {
                path.add(new int[]{mv[0], mv[1], mv[2], mv[3], choice});
                count += countMoves(fen, path, depth - 1);
                path.remove(path.size() - 1);
            }
        }
        return count;
    }

    private static ChessMatch position(String fen, List<int[]> path) throws Exception {
        ChessMatch match = fromFen(fen);
        for (int[] mv : path) {
            match.performChessMove(square(mv[0], mv[1]), square(mv[2], mv[3]));
            if (mv[4] != 0) {
                match.replacePromotedPiece(String.valueOf((char) mv[4]));
            }
        }
        return match;
    }

    // ---------- cenários ----------

    private static void castling() {
        // O bispo preto em a6 ataca f1: o rei branco não pode passar por lá.
        ChessMatch m = play("e2e4 b7b6 g1f3 c8a6 g2g3 g8f6 f1g2 b8c6");
        check(!m.possibleMoves(pos("e1"))[7][6], "roque pequeno não aparece quando f1 está atacada");
        check(isIllegal(m, "e1g1"), "roque pequeno recusado quando o rei passaria por casa atacada");
        play(m, "d2d3 e7e6 e1g1"); // d3 fecha a diagonal: agora pode
        check(pieceAt(m, "g1") instanceof King && pieceAt(m, "f1") instanceof Rook, "roque pequeno: rei em g1 e torre em f1");

        // Roque grande dos dois lados.
        m = play("d2d4 d7d5 b1c3 b8c6 c1f4 c8f5 d1d2 d8d7 e1c1 e8c8");
        check(pieceAt(m, "c1") instanceof King && pieceAt(m, "d1") instanceof Rook, "roque grande das brancas");
        check(pieceAt(m, "c8") instanceof King && pieceAt(m, "d8") instanceof Rook, "roque grande das pretas");

        // Se a torre de a1 já se moveu, não há roque grande (mesmo com a de h1 parada).
        m = play("d2d4 d7d5 b1c3 b8c6 c1f4 c8f5 d1d2 d8d7 a1b1 e7e6 b1a1 f8e7");
        check(isIllegal(m, "e1c1"), "roque grande recusado depois que a torre de a1 se moveu");
    }

    private static void promotionRecalculatesCheck() {
        String untilPromotion = "a2a4 b7b5 a4b5 a7a6 b5a6 c8b7 a6b7 b8c6 h2h3 e7e6 g2g3 d8f6 b7a8";

        ChessMatch queen = play(untilPromotion);
        check(queen.getPromoted() instanceof Queen && queen.getCheck(), "promoção a dama em a8 dá xeque no rei em e8");

        ChessMatch knight = play(untilPromotion);
        knight.replacePromotedPiece("N");
        check(pieceAt(knight, "a8") instanceof Knight, "peão promovido a cavalo");
        check(!knight.getCheck() && !knight.isGameOver(), "promoção a cavalo NÃO dá xeque: o xeque é recalculado");
        check(knight.getCurrentPlayer() == Color.BLACK && knight.getTurn() == 14, "depois da promoção a vez é das pretas");

        ChessMatch rook = play(untilPromotion);
        rook.replacePromotedPiece("R");
        check(rook.getCheck() && !rook.getCheckmate(), "promoção a torre dá xeque, sem mate");
    }

    private static void enPassantCanAnswerCheck() throws Exception {
        // g2-g4 dá xeque no rei em h5. A única resposta é capturar en passant com o peão de h4.
        ChessMatch m = fromFen("8/4B3/R7/7k/7p/5K2/6P1/8 w - -");
        play(m, "g2g4");
        check(m.getCheck(), "avanço duplo do peão dá xeque");
        check(!m.getCheckmate() && !m.isGameOver(), "não é mate: as pretas podem capturar en passant");
        play(m, "h4g3");
        check(pieceAt(m, "g4") == null && pieceAt(m, "g3") instanceof Pawn, "captura en passant remove o peão de g4");
    }

    private static void stalemate() {
        // Afogamento mais curto conhecido (Sam Loyd, 10 lances).
        ChessMatch m = play("e2e3 a7a5 d1h5 a8a6 h5a5 h7h5 h2h4 a6h6 a5c7 f7f6 c7d7 e8f7 d7b7 d8d3 b7b8 d3h7 b8c8 f7g6 c8e6");
        check(m.getStalemate() && !m.getCheck() && !m.getCheckmate(), "afogamento detectado: pretas sem lance legal e sem xeque");
        check(m.isGameOver() && m.getCurrentPlayer() == Color.BLACK, "afogamento encerra a partida");
        check(isIllegal(m, "g6g5"), "nenhum lance é aceito depois do fim da partida");
    }

    private static void foolsMate() {
        ChessMatch m = play("f2f3 e7e5 g2g4 d8h4");
        check(m.getCheckmate() && m.isGameOver() && !m.getStalemate(), "mate do louco detectado");
        check(m.getCurrentPlayer() == Color.BLACK, "o vencedor (pretas) fica como jogador atual");
        check(isIllegal(m, "e1f2"), "nenhum lance é aceito depois do xeque-mate");
    }

    // ---------- apoio ----------

    private static ChessMatch play(String moves) {
        return play(new ChessMatch(), moves);
    }

    private static ChessMatch play(ChessMatch match, String moves) {
        for (String mv : moves.trim().split("\\s+")) {
            match.performChessMove(pos(mv.substring(0, 2)), pos(mv.substring(2, 4)));
        }
        return match;
    }

    private static boolean isIllegal(ChessMatch match, String move) {
        try {
            match.performChessMove(pos(move.substring(0, 2)), pos(move.substring(2, 4)));
            return false;
        } catch (ChessException e) {
            return true;
        }
    }

    private static ChessPosition pos(String s) {
        return new ChessPosition(s.charAt(0), s.charAt(1) - '0');
    }

    private static ChessPosition square(int row, int column) {
        return new ChessPosition((char) ('a' + column), 8 - row);
    }

    private static ChessPiece pieceAt(ChessMatch match, String square) {
        ChessPosition p = pos(square);
        return match.getPieces()[8 - p.getRow()][p.getColumn() - 'a'];
    }

    private static Field field(String name) throws Exception {
        Field f = ChessMatch.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** Monta uma posição a partir da notação FEN (peças, quem joga, roques, en passant). */
    @SuppressWarnings("unchecked")
    private static ChessMatch fromFen(String fen) throws Exception {
        String[] parts = fen.split(" ");
        ChessMatch match = new ChessMatch();
        Board board = (Board) field("board").get(match);
        List<Piece> onBoard = (List<Piece>) field("piecesOnTheBoard").get(match);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board.removePiece(new Position(r, c));
            }
        }
        onBoard.clear();

        String[] rows = parts[0].split("/");
        for (int r = 0; r < 8; r++) {
            int c = 0;
            for (char ch : rows[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    c += ch - '0';
                    continue;
                }
                Color color = Character.isUpperCase(ch) ? Color.WHITE : Color.BLACK;
                ChessPiece piece = switch (Character.toUpperCase(ch)) {
                    case 'K' -> new King(board, color, match);
                    case 'Q' -> new Queen(board, color);
                    case 'R' -> new Rook(board, color);
                    case 'B' -> new Bishop(board, color);
                    case 'N' -> new Knight(board, color);
                    default -> new Pawn(board, color, match);
                };
                board.placePiece(piece, new Position(r, c));
                onBoard.add(piece);
                // peão fora da fileira inicial e rei fora de e1/e8 já se moveram
                boolean pawnMoved = piece instanceof Pawn && r != (color == Color.WHITE ? 6 : 1);
                boolean kingMoved = piece instanceof King && !(c == 4 && r == (color == Color.WHITE ? 7 : 0));
                if (pawnMoved || kingMoved) {
                    piece.increaseMoveCount();
                }
                c++;
            }
        }
        // sem direito de roque: a torre conta como já movida
        markRookMoved(board, 7, 7, parts[2].contains("K"));
        markRookMoved(board, 7, 0, parts[2].contains("Q"));
        markRookMoved(board, 0, 7, parts[2].contains("k"));
        markRookMoved(board, 0, 0, parts[2].contains("q"));

        Color side = parts[1].equals("w") ? Color.WHITE : Color.BLACK;
        field("currentPlayer").set(match, side);
        if (!parts[3].equals("-")) {
            int c = parts[3].charAt(0) - 'a';
            int r = 8 - (parts[3].charAt(1) - '0');
            field("enPassantVulnerable").set(match, board.piece(side == Color.WHITE ? r + 1 : r - 1, c));
        }
        Method testCheck = ChessMatch.class.getDeclaredMethod("testCheck", Color.class);
        testCheck.setAccessible(true);
        field("check").set(match, testCheck.invoke(match, side));
        return match;
    }

    private static void markRookMoved(Board board, int row, int column, boolean canCastle) {
        Piece piece = board.piece(row, column);
        if (!canCastle && piece instanceof Rook) {
            ((ChessPiece) piece).increaseMoveCount();
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("FALHOU: " + what);
        }
        passed++;
    }
}
