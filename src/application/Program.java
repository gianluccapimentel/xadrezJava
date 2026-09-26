package application;

import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.Scanner;

public class Program {

    static void main(String[] args) {
        ChessMatch chessMatch = new ChessMatch();

        Scanner sc = new Scanner(System.in);

        while(true) {
            UI.printBoard(chessMatch.getPieces());
            System.out.println();
            System.out.println("Origem: ");
            ChessPosition source = UI.readChessPosition(sc);

            System.out.println();
            System.out.println("Destino: ");
            ChessPosition target = UI.readChessPosition(sc);
            ChessPiece capturedPiece = chessMatch.performChessMove(source, target);
        }

    }

}
