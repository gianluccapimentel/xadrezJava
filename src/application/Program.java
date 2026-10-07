package application;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

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
