package chess;

import boardGame.Board;
import boardGame.Piece;
import boardGame.Position;
import chess.pieces.*;

import java.util.ArrayList;
import java.util.List;

public class ChessMatch {

    private int turn;
    private int baseTurn;
    private Color currentPlayer;
    private Board board;
    private boolean check;
    private boolean checkMate;
    private boolean stalemate;
    private ChessPiece enPassantVulnerable;
    private ChessPiece promoted;

    private List<Piece> piecesOnTheBoard =  new ArrayList<>();
    private List<Piece> capturedPieces =  new ArrayList<>();

    public ChessMatch() {
        board = new Board(8, 8);
        turn = 1;
        currentPlayer = Color.WHITE;
        initialSetup();
    }

    public int getTurn() {
        return turn;
    }

    public Color getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean getCheck() {
        return check;
    }

    public ChessPiece getEnPassantVulnerable() {
        return enPassantVulnerable;
    }

    public boolean getCheckmate() {
        return checkMate;
    }

    /** Afogamento: o jogador da vez não está em xeque e não tem lance legal. É empate. */
    public boolean getStalemate() {
        return stalemate;
    }

    /** A partida acabou (xeque-mate ou empate por afogamento). */
    public boolean isGameOver() {
        return checkMate || stalemate;
    }

    public ChessPiece[][] getPieces() {
        ChessPiece[][] mat = new ChessPiece[board.getRows()][board.getColumns()];

        for (int i = 0; i < board.getRows(); i++) {
            for (int j = 0; j < board.getColumns(); j++) {
                mat[i][j] = (ChessPiece) board.piece(i, j);
            }
        }

        return mat;
    }

    public ChessPiece getPromoted() {
        return promoted;
    }

    public boolean[][] possibleMoves(ChessPosition sourcePosition) {
        Position position = sourcePosition.toPosition();
        validateSourcePosition(position);
        return board.piece(position).possibleMoves();
    }

    public ChessPiece performChessMove(ChessPosition sourcePosition, ChessPosition targetPosition) {
        Position source = sourcePosition.toPosition();
        Position target = targetPosition.toPosition();
        if (isGameOver()) {
            throw new ChessException("A partida ja terminou");
        }
        validateSourcePosition(source);
        validateTargetPosition(source, target);
        Piece capturedPiece = makeMove(source, target);
        baseTurn = turn;

        if (testCheck(currentPlayer)) {
            undoMove(source, target, capturedPiece);
            throw new ChessException("Voce nao pode se colocar em cheque");
        }

        ChessPiece movedPiece = (ChessPiece)board.piece(target);

        //promocao (provisoriamente dama; o jogador pode trocar com replacePromotedPiece)
        promoted = null;
        if (movedPiece instanceof Pawn) {
            if (movedPiece.getColor() == Color.WHITE && target.getRow() == 0 || movedPiece.getColor() == Color.BLACK && target.getRow() == 7) {
                promoted = (ChessPiece)board.piece(target);
                promoted = swapPromotedPiece("Q");
            }
        }

        // enPassant: atualizado ANTES de testar o mate, porque a resposta do
        // adversário pode ser justamente capturar en passant
        if (movedPiece instanceof Pawn && (target.getRow() == source.getRow() - 2 || target.getRow() == source.getRow() + 2)) {
            enPassantVulnerable = movedPiece;
        } else {
            enPassantVulnerable = null;
        }

        evaluateEndOfMove(movedPiece.getColor());

        return (ChessPiece)capturedPiece;
    }

    /** Calcula xeque, xeque-mate e afogamento do adversário e passa a vez. */
    private void evaluateEndOfMove(Color mover) {
        Color other = opponent(mover);
        check = testCheck(other);
        checkMate = check && !hasLegalMove(other);
        stalemate = !check && !hasLegalMove(other);
        // no xeque-mate a vez fica com o vencedor (é assim que a tela mostra o vencedor)
        currentPlayer = checkMate ? mover : other;
        turn = baseTurn + (checkMate ? 0 : 1);
    }

    private void validateSourcePosition(Position position) {
        if (!board.thereIsAPiece(position)) {
            throw new ChessException("Nao existe peca na posicao de origem.");
        }
        if (currentPlayer != ((ChessPiece)board.piece(position)).getColor()) {
            throw new ChessException("A peca escolhida nao e sua!!!");
        }
        if(!board.piece(position).isThereAnyPossibleMove()) {
            throw new ChessException("Nao existem movimentos possiveis para a peca escolhida.");
        }

    }

    private void validateTargetPosition(Position source, Position target) {
        if (!board.piece(source).possibleMove(target)) {
            throw new ChessException("A peca escolhida nao pode se mover para a posicao de destino");
        }
    }

    /**
     * Troca a peça promovida pela escolhida (B, N, R ou Q) e recalcula xeque,
     * xeque-mate e afogamento, que dependem de qual peça ficou na casa.
     */
    public ChessPiece replacePromotedPiece(String type) {
        if(promoted == null) {
            throw new IllegalStateException("Não há peça para ser promovida");
        }
        if (!type.equals("B") && !type.equals("R") && !type.equals("Q") && !type.equals("N")) {
            return promoted;
        }
        ChessPiece newPiece = swapPromotedPiece(type);
        promoted = newPiece;
        evaluateEndOfMove(newPiece.getColor());
        return newPiece;
    }

    private ChessPiece swapPromotedPiece(String type) {

        Position pos = promoted.getChessPosition().toPosition();
        Piece p = board.removePiece(pos);
        piecesOnTheBoard.remove(p);

        ChessPiece newPiece = newPiece(type,promoted.getColor());
        board.placePiece(newPiece, pos);
        piecesOnTheBoard.add(newPiece);

        return newPiece;
    }

    private ChessPiece newPiece(String type, Color color) {
        return switch (type) {
            case "B" -> new Bishop(board, color);
            case "N" -> new Knight(board, color);
            case "Q" -> new Queen(board, color);
            default -> new Rook(board, color);
        };
    }

    private Piece makeMove(Position source, Position target) {
        ChessPiece p = (ChessPiece)board.removePiece(source);
        p.increaseMoveCount();
        Piece capturedPiece = board.removePiece(target);
        board.placePiece(p, target);

        if (capturedPiece != null) {
            piecesOnTheBoard.remove(capturedPiece);
            capturedPieces.add(capturedPiece);
        }

        // near castling
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);
            ChessPiece rook = (ChessPiece)board.removePiece(sourceT);
            board.placePiece(rook, targetT);
            rook.increaseMoveCount();
        }
        // long castling
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4);
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);
            ChessPiece rook = (ChessPiece)board.removePiece(sourceT);
            board.placePiece(rook, targetT);
            rook.increaseMoveCount();
        }
        // enPassant
        if (p instanceof Pawn) {
            if(source.getColumn() != target.getColumn()) {
                if(capturedPiece == null) {
                    Position pawnPosition;
                    if (p.getColor() == Color.WHITE) {
                        pawnPosition = new Position(target.getRow() + 1, target.getColumn());
                    } else {
                        pawnPosition = new Position(target.getRow() - 1, target.getColumn());
                    }
                    capturedPiece = board.removePiece(pawnPosition);
                    capturedPieces.add(capturedPiece);
                    piecesOnTheBoard.remove(capturedPiece);
                }
            }
        }

        return capturedPiece;
    }

    private void undoMove(Position source, Position target, Piece capturedPiece) {
        ChessPiece p = (ChessPiece)board.removePiece(target);
        p.decreaseMoveCount();
        board.placePiece(p, source);

        if (capturedPiece != null) {
            board.placePiece(capturedPiece, target);
            capturedPieces.remove(capturedPiece);
            piecesOnTheBoard.add(capturedPiece);
        }

        // near castling
        if (p instanceof King && target.getColumn() == source.getColumn() + 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() + 3);
            Position targetT = new Position(source.getRow(), source.getColumn() + 1);
            ChessPiece rook = (ChessPiece)board.removePiece(targetT);
            board.placePiece(rook, sourceT);
            rook.decreaseMoveCount();
        }
        // long castling
        if (p instanceof King && target.getColumn() == source.getColumn() - 2) {
            Position sourceT = new Position(source.getRow(), source.getColumn() - 4);
            Position targetT = new Position(source.getRow(), source.getColumn() - 1);
            ChessPiece rook = (ChessPiece)board.removePiece(targetT);
            board.placePiece(rook, sourceT);
            rook.decreaseMoveCount();
        }
        // enPassant
        if (p instanceof Pawn) {
            if(source.getColumn() != target.getColumn()) {
                if(capturedPiece == enPassantVulnerable) {
                    ChessPiece pawn = (ChessPiece)board.removePiece(target);
                    Position pawnPosition;
                    if (p.getColor() == Color.WHITE) {
                        pawnPosition = new Position(3, target.getColumn());
                    } else {
                        pawnPosition = new Position(4, target.getColumn());
                    }
                    board.placePiece(pawn, pawnPosition);
                }
            }
        }
    }

    private ChessPiece king(Color color) {
        List<Piece> list = piecesOnTheBoard.
                stream().
                filter(x -> ((ChessPiece)x).getColor() == color).
                toList();
        for (Piece p : list) {
            if (p instanceof King) {
                return (ChessPiece)p;
            }
        }
        throw new IllegalStateException("Nao existe rei " + color + " na partida");
    }

    private boolean testCheck(Color color) {
        Position kingPosition = king(color).getChessPosition().toPosition();
        List<Piece> opponentPieces = piecesOnTheBoard.
                stream().
                filter(x -> ((ChessPiece)x).getColor() == opponent(color)).
                toList();

        for (Piece p : opponentPieces) {
            boolean[][] mat = p.possibleMoves();
            if (mat[kingPosition.getRow()][kingPosition.getColumn()]) {
                return true;
            }
        }
        return false;
    }

    /** O jogador tem pelo menos um lance que não deixa o próprio rei em xeque? */
    private boolean hasLegalMove(Color color) {
        List<Piece> list = piecesOnTheBoard.
                stream().
                filter(x -> ((ChessPiece)x).getColor() == color).
                toList();

        for (Piece p : list) {
            boolean[][] mat = p.possibleMoves();
            for (int i = 0; i < board.getRows(); i++) {
                for (int j = 0; j < board.getColumns(); j++) {
                    if (mat[i][j]) {
                        Position source = ((ChessPiece)p).getChessPosition().toPosition();
                        Position target = new Position(i, j);
                        Piece capturedPiece = makeMove(source, target);
                        boolean testCheck = testCheck(color);
                        undoMove(source, target, capturedPiece);
                        if (!testCheck) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * A casa é atacada por alguma peça da cor indicada? Usado no roque: o rei
     * não pode passar por casa atacada. Rei e peão são tratados pela geometria,
     * sem chamar possibleMoves, para não entrar em recursão com o roque.
     */
    public boolean isSquareAttacked(Position square, Color byColor) {
        for (Piece piece : new ArrayList<>(piecesOnTheBoard)) {
            ChessPiece p = (ChessPiece) piece;
            if (p.getColor() != byColor) {
                continue;
            }
            Position from = p.getChessPosition().toPosition();
            int dr = square.getRow() - from.getRow();
            int dc = square.getColumn() - from.getColumn();
            if (p instanceof King) {
                if (Math.abs(dr) <= 1 && Math.abs(dc) <= 1 && (dr != 0 || dc != 0)) {
                    return true;
                }
            } else if (p instanceof Pawn) {
                int forward = (p.getColor() == Color.WHITE) ? -1 : 1;
                if (dr == forward && Math.abs(dc) == 1) {
                    return true;
                }
            } else if (p.possibleMove(square)) {
                return true;
            }
        }
        return false;
    }

    private Color opponent(Color color) {
        return (color == Color.WHITE) ? Color.BLACK : Color.WHITE;
    }

    private void placeNewPiece(char column, int row, ChessPiece piece) {
        board.placePiece(piece, new ChessPosition(column, row).toPosition());
        piecesOnTheBoard.add(piece);
    }

    private void initialSetup() {

        placeNewPiece('a', 1, new Rook(board, Color.WHITE));
        placeNewPiece('b', 1, new Knight(board, Color.WHITE));
        placeNewPiece('c', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('d', 1, new Queen(board, Color.WHITE));
        placeNewPiece('e', 1, new King(board, Color.WHITE, this));
        placeNewPiece('f', 1, new Bishop(board, Color.WHITE));
        placeNewPiece('g', 1, new Knight(board, Color.WHITE));
        placeNewPiece('h', 1, new Rook(board, Color.WHITE));
        placeNewPiece('a', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('b', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('c', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('d', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('e', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('f', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('g', 2, new Pawn(board, Color.WHITE, this));
        placeNewPiece('h', 2, new Pawn(board, Color.WHITE, this));

        placeNewPiece('a', 8, new Rook(board, Color.BLACK));
        placeNewPiece('b', 8, new Knight(board, Color.BLACK));
        placeNewPiece('c', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('d', 8, new Queen(board, Color.BLACK));
        placeNewPiece('e', 8, new King(board, Color.BLACK, this));
        placeNewPiece('f', 8, new Bishop(board, Color.BLACK));
        placeNewPiece('g', 8, new Knight(board, Color.BLACK));
        placeNewPiece('h', 8, new Rook(board, Color.BLACK));
        placeNewPiece('a', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('b', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('c', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('d', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('e', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('f', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('g', 7, new Pawn(board, Color.BLACK, this));
        placeNewPiece('h', 7, new Pawn(board, Color.BLACK, this));
    }
}
