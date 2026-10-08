package application;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.*;

public class Program {

    static void main(String[] args) {

            ChessMatch chessMatch = new ChessMatch();
            List<ChessPiece> captured = new ArrayList<>();
            Scanner sc = new Scanner(System.in);


            while (!chessMatch.getCheckmate()) {
                try {
                    UI.clearScreen();
                    UI.printMatch(chessMatch, captured);
                    System.out.println();
                    System.out.println("Origem: ");
                    ChessPosition source = UI.readChessPosition(sc);
                    boolean[][] possibleMoves = chessMatch.possibleMoves(source);
                    UI.clearScreen();
                    UI.printBoard(chessMatch.getPieces(), possibleMoves);
                    System.out.println();

                    System.out.println("Destino: ");
                    ChessPosition target = UI.readChessPosition(sc);

                    ChessPiece capturedPiece = chessMatch.performChessMove(source, target);
                    if (capturedPiece != null) {
                        captured.add(capturedPiece);
                    }

                    if (chessMatch.getPromoted() != null) {
                        System.out.println("Digite a peça para promoção (B/N/R/Q): ");
                        String type = sc.nextLine().toUpperCase();
                        while (!type.equals("B") && !type.equals("R") && !type.equals("Q") && !type.equals("N")) {
                            System.out.println("Valor inválido! Digite a peça para promoção (B/N/R/Q): ");
                            type = sc.nextLine().toUpperCase();
                        }
                        chessMatch.replacePromotedPiece(type);
                    }
                }
                catch (ChessException | InputMismatchException e ) {
                    System.out.println(e.getMessage());
                    sc.nextLine();
                }

            }
            UI.clearScreen();
            UI.printMatch(chessMatch, captured);
    }

}
