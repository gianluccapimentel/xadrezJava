package application;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;
import network.LocalChannel;
import network.MatchProtocol;
import network.Message;
import network.ProtocolException;

import java.util.*;

public class Program {

    public static void main(String[] args) throws InterruptedException {
        Scanner sc = new Scanner(System.in);
        System.out.println("1 - Jogo local (original)");
        System.out.println("2 - Jogo local pelo protocolo de mensagens");
        System.out.print("Escolha: ");
        String option = sc.nextLine().trim();

        if (option.equals("2")) {
            playThroughProtocol(sc);
        } else {
            playLocal(sc);
        }
    }

    /** O jogo original: os dois jogadores no mesmo tabuleiro. */
    private static void playLocal(Scanner sc) {
        ChessMatch chessMatch = new ChessMatch();
        List<ChessPiece> captured = new ArrayList<>();

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
                    chessMatch.replacePromotedPiece(readPromotion(sc));
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

    /**
     * Cada jogador tem o seu próprio tabuleiro (MatchProtocol) e eles só
     * conversam por mensagens de texto num canal. Aqui o canal é local;
     * no sprint 2 ele vira um socket e cada jogador roda num processo.
     */
    private static void playThroughProtocol(Scanner sc) throws InterruptedException {
        MatchProtocol[] side = {new MatchProtocol(1), new MatchProtocol(2)};
        LocalChannel[] channel = LocalChannel.pair();
        List<String> log = new ArrayList<>();

        while (!side[0].isGameOver()) {
            int cur = side[0].getVez().getVez() - 1;   // 0 = jogador 1, 1 = jogador 2
            int other = 1 - cur;
            MatchProtocol me = side[cur];
            try {
                UI.clearScreen();
                UI.printMatch(me.getMatch(), me.getCaptured());
                printLog(log);
                System.out.println();
                System.out.println("Jogador " + (cur + 1) + " - Origem: ");
                ChessPosition source = UI.readChessPosition(sc);
                boolean[][] possibleMoves = me.getMatch().possibleMoves(source);
                UI.clearScreen();
                UI.printBoard(me.getMatch().getPieces(), possibleMoves);
                System.out.println();
                System.out.println("Jogador " + (cur + 1) + " - Destino: ");
                ChessPosition target = UI.readChessPosition(sc);

                List<Message> sent = me.localMove(source, target, () -> readPromotion(sc).charAt(0));
                for (Message m : sent) {
                    channel[cur].send(m);
                    log.add("jogador " + (cur + 1) + " enviou: " + m.toWire());
                }

                // o adversário lê o canal e aplica no tabuleiro dele
                while (channel[other].hasPending()) {
                    for (Message reply : side[other].receive(channel[other].receive())) {
                        channel[other].send(reply);
                        log.add("jogador " + (other + 1) + " respondeu: " + reply.toWire());
                    }
                }
                while (channel[cur].hasPending()) {
                    side[cur].receive(channel[cur].receive());
                }
            }
            catch (ChessException | ProtocolException | InputMismatchException e) {
                System.out.println(e.getMessage());
                sc.nextLine();
            }
        }
        UI.clearScreen();
        UI.printMatch(side[0].getMatch(), side[0].getCaptured());
        printLog(log);
    }

    private static void printLog(List<String> log) {
        if (log.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("Mensagens trocadas (últimas 4):");
        for (String line : log.subList(Math.max(0, log.size() - 4), log.size())) {
            System.out.println("  " + line);
        }
    }

    private static String readPromotion(Scanner sc) {
        System.out.println("Digite a peça para promoção (B/N/R/Q): ");
        String type = sc.nextLine().toUpperCase();
        while (!type.equals("B") && !type.equals("R") && !type.equals("Q") && !type.equals("N")) {
            System.out.println("Valor inválido! Digite a peça para promoção (B/N/R/Q): ");
            type = sc.nextLine().toUpperCase();
        }
        return type;
    }
}
