package application;

import chess.ChessException;
import chess.ChessMatch;
import chess.ChessPiece;
import chess.ChessPosition;
import network.LocalChannel;
import network.MatchProtocol;
import network.Message;
import network.NetworkGame;
import network.ProtocolException;
import network.SharedGameState;
import network.SocketMessageChannel;

import java.io.IOException;
import java.util.*;

public class Program {

    public static void main(String[] args) throws InterruptedException {
        Scanner sc = new Scanner(System.in);
        System.out.println("1 - Jogo local (original)");
        System.out.println("2 - Jogo local pelo protocolo de mensagens");
        System.out.println("3 - Criar partida em rede (servidor, brancas)");
        System.out.println("4 - Entrar em partida em rede (cliente, pretas)");
        System.out.println("5 - Assistir partida pela memória compartilhada");
        System.out.print("Escolha: ");
        String option = sc.nextLine().trim();

        switch (option) {
            case "2" -> playThroughProtocol(sc);
            case "3" -> playNetwork(sc, true);
            case "4" -> playNetwork(sc, false);
            case "5" -> watch(sc);
            default -> playLocal(sc);
        }
    }

    /**
     * Partida em rede: cada jogador roda o programa no seu terminal.
     * O servidor joga de brancas (jogador 1) e o cliente de pretas (jogador 2).
     */
    private static void playNetwork(Scanner sc, boolean host) throws InterruptedException {
        int me = host ? 1 : 2;
        System.out.print("Porta [" + SocketMessageChannel.DEFAULT_PORT + "]: ");
        String portText = sc.nextLine().trim();
        int port = portText.isEmpty() ? SocketMessageChannel.DEFAULT_PORT : Integer.parseInt(portText);

        SocketMessageChannel channel;
        SharedGameState shared = null;
        try {
            if (host) {
                shared = openSharedMemory(port, true);
                System.out.println("Aguardando adversário na porta " + port + "...");
                channel = SocketMessageChannel.host(port);
            } else {
                System.out.print("Endereço do servidor [localhost]: ");
                String address = sc.nextLine().trim();
                channel = SocketMessageChannel.join(address.isEmpty() ? "localhost" : address, port);
                shared = openSharedMemory(port, false);
            }
        } catch (IOException e) {
            System.out.println("Não foi possível conectar: " + e.getMessage());
            return;
        }
        System.out.println("Conectado a " + channel.remoteAddress());

        NetworkGame game = new NetworkGame(me, channel, shared);
        game.start();
        String who = host ? "BRANCAS (jogador 1)" : "PRETAS (jogador 2)";
        String error = null;

        while (!game.isOver()) {
            if (!game.getProtocol().getVez().isMyTurn()) {
                drawNetwork(game, who, error);
                System.out.println("Aguardando o lance do adversário...");
                error = null;
                game.waitMyTurn();
                continue;
            }
            try {
                drawNetwork(game, who, error);
                error = null;
                System.out.println("Sua vez. Origem (ou \"sair\"): ");
                ChessPosition source = readPositionOrQuit(sc);
                if (source == null) {
                    game.quit();
                    break;
                }
                boolean[][] possibleMoves = game.getProtocol().getMatch().possibleMoves(source);
                UI.clearScreen();
                UI.printBoard(game.getProtocol().getMatch().getPieces(), possibleMoves);
                System.out.println();
                System.out.println("Destino: ");
                ChessPosition target = readPositionOrQuit(sc);
                if (target == null) {
                    game.quit();
                    break;
                }
                game.play(source, target, () -> readPromotion(sc).charAt(0));
            }
            catch (ChessException | ProtocolException | InputMismatchException e) {
                error = e.getMessage();
            }
            catch (IOException e) {
                System.out.println("Conexão perdida: " + e.getMessage());
                break;
            }
        }

        drawNetwork(game, who, null);
        if (game.getProtocol().getMatch().getCheckmate()) {
            boolean iWon = game.getProtocol().getMatch().getCurrentPlayer() == (host ? chess.Color.WHITE : chess.Color.BLACK);
            System.out.println(iWon ? "Você venceu!" : "Você perdeu.");
        } else if (game.getProtocol().isOpponentQuit()) {
            System.out.println("O adversário saiu da partida.");
        } else {
            System.out.println("Você saiu da partida.");
        }
        game.close();
    }

    /**
     * Espectador: um terceiro processo que não usa socket nenhum. Ele só lê a
     * memória compartilhada (com a mesma trava dos jogadores) e redesenha o
     * tabuleiro quando a versão muda.
     */
    private static void watch(Scanner sc) throws InterruptedException {
        System.out.print("Porta da partida [" + SocketMessageChannel.DEFAULT_PORT + "]: ");
        String portText = sc.nextLine().trim();
        int port = portText.isEmpty() ? SocketMessageChannel.DEFAULT_PORT : Integer.parseInt(portText);

        try (SharedGameState shared = SharedGameState.open(SharedGameState.defaultPath(port))) {
            long lastVersion = -1;
            while (true) {
                SharedGameState.Snapshot snap = shared.read();
                if (snap != null && snap.versao() != lastVersion) {
                    lastVersion = snap.versao();
                    UI.clearScreen();
                    System.out.println("Assistindo pela memória compartilhada: " + shared.getPath());
                    printSnapshotBoard(snap.board());
                    System.out.println();
                    System.out.println("Lances: " + snap.lances() + "   Último: " + (snap.lastMove().isEmpty() ? "-" : snap.lastMove()));
                    System.out.println("Vez do jogador " + snap.vez() + "   Situação: " + snap.statusText());
                    System.out.println("Versão da memória: " + snap.versao());
                    if (snap.status() == SharedGameState.MATE || snap.status() == SharedGameState.DESISTENCIA) {
                        return;
                    }
                } else if (snap == null && lastVersion == -1) {
                    System.out.println("Esperando a partida começar...");
                    lastVersion = 0;
                }
                Thread.sleep(300);
            }
        } catch (IOException e) {
            System.out.println("Não foi possível abrir a memória compartilhada: " + e.getMessage());
        }
    }

    private static void printSnapshotBoard(String board) {
        for (int i = 0; i < 8; i++) {
            System.out.print((8 - i) + " ");
            for (int j = 0; j < 8; j++) {
                char c = board.charAt(i * 8 + j);
                if (c == '.') {
                    System.out.print("-" + UI.ANSI_RESET + " ");
                } else if (Character.isUpperCase(c)) {
                    System.out.print(UI.ANSI_WHITE + c + UI.ANSI_RESET + " ");
                } else {
                    System.out.print(UI.ANSI_YELLOW + Character.toUpperCase(c) + UI.ANSI_RESET + " ");
                }
            }
            System.out.println();
        }
        System.out.println("  a b c d e f g h");
    }

    /** Abre a memória compartilhada da partida. O servidor grava o tabuleiro inicial. */
    private static SharedGameState openSharedMemory(int port, boolean host) {
        try {
            SharedGameState shared = SharedGameState.open(SharedGameState.defaultPath(port));
            if (host) {
                shared.reset(new ChessMatch());
            }
            return shared;
        } catch (IOException e) {
            System.out.println("Memória compartilhada indisponível: " + e.getMessage());
            return null;
        }
    }

    private static void drawNetwork(NetworkGame game, String who, String error) {
        UI.clearScreen();
        System.out.println("Você joga com as " + who);
        UI.printMatch(game.getProtocol().getMatch(), game.getProtocol().getCaptured());
        System.out.println(game.getSharedStatus());
        List<String> log = game.lastLog(4);
        if (!log.isEmpty()) {
            System.out.println();
            System.out.println("Mensagens (últimas 4):");
            log.forEach(line -> System.out.println("  " + line));
        }
        String peerError = game.getProtocol().takeLastError();
        if (peerError != null) {
            System.out.println("O adversário recusou a mensagem: " + peerError);
        }
        if (error != null) {
            System.out.println(error);
        }
        System.out.println();
    }

    /** Lê uma casa (ex.: e2). Devolve null se o jogador digitar "sair". */
    private static ChessPosition readPositionOrQuit(Scanner sc) {
        String s = sc.nextLine().trim();
        if (s.equalsIgnoreCase("sair")) {
            return null;
        }
        try {
            return new ChessPosition(s.charAt(0), Integer.parseInt(s.substring(1)));
        } catch (RuntimeException e) {
            throw new InputMismatchException("Erro lendo posicao de xadrez.");
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
