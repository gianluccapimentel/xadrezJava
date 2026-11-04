package network;

import chess.ChessPosition;

/**
 * Uma mensagem do protocolo de rede do xadrez.
 *
 * Formato de um lance:  <vez><peça><origem><destino>[=<promoção>]
 *   1Pe2e4    -> jogador 1 (brancas) move o peão de e2 para e4
 *   2Ng8f6    -> jogador 2 (pretas) move o cavalo de g8 para f6
 *   1Pe7e8=N  -> jogador 1 promove o peão em e8 a cavalo
 *
 * Mensagens de controle começam com '#':
 *   #BEMVINDO 2    servidor avisa ao cliente que ele é o jogador 2
 *   #XEQUE 2       o jogador 2 está em xeque
 *   #MATE 1        xeque-mate, o jogador 1 venceu
 *   #ERRO texto    lance recusado, com o motivo
 *   #SAIR 1        o jogador 1 abandonou a partida
 *
 * Peças: K rei, Q dama, R torre, B bispo, N cavalo, P peão
 * (as mesmas letras que o jogo já usa no tabuleiro e na promoção).
 */
public final class Message {

    public enum Type { MOVE, BEMVINDO, XEQUE, MATE, ERRO, SAIR }

    private static final String PIECES = "KQRBNP";
    private static final String PROMOTIONS = "QRBN";

    private final Type type;
    private final int player;          // 1 ou 2 (vez); 0 quando não se aplica
    private final char piece;          // só em MOVE
    private final ChessPosition source;
    private final ChessPosition target;
    private final Character promotion; // null quando não há promoção
    private final String text;         // só em ERRO

    private Message(Type type, int player, char piece, ChessPosition source,
                    ChessPosition target, Character promotion, String text) {
        this.type = type;
        this.player = player;
        this.piece = piece;
        this.source = source;
        this.target = target;
        this.promotion = promotion;
        this.text = text;
    }

    // ---------- fábricas ----------

    public static Message move(int player, char piece, ChessPosition source, ChessPosition target) {
        return move(player, piece, source, target, null);
    }

    public static Message move(int player, char piece, ChessPosition source,
                               ChessPosition target, Character promotion) {
        checkPlayer(player);
        piece = Character.toUpperCase(piece);
        if (PIECES.indexOf(piece) < 0) {
            throw new ProtocolException("Peça inválida: " + piece);
        }
        if (promotion != null) {
            promotion = Character.toUpperCase(promotion);
            if (piece != 'P' || PROMOTIONS.indexOf(promotion) < 0) {
                throw new ProtocolException("Promoção inválida: " + promotion);
            }
        }
        return new Message(Type.MOVE, player, piece, source, target, promotion, null);
    }

    public static Message bemVindo(int player) { checkPlayer(player); return new Message(Type.BEMVINDO, player, ' ', null, null, null, null); }
    public static Message xeque(int player)    { checkPlayer(player); return new Message(Type.XEQUE, player, ' ', null, null, null, null); }
    public static Message mate(int winner)     { checkPlayer(winner); return new Message(Type.MATE, winner, ' ', null, null, null, null); }
    public static Message sair(int player)     { checkPlayer(player); return new Message(Type.SAIR, player, ' ', null, null, null, null); }

    public static Message erro(String text) {
        String clean = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        return new Message(Type.ERRO, 0, ' ', null, null, null, clean);
    }

    // ---------- texto -> mensagem ----------

    public static Message parse(String line) {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("Mensagem vazia");
        }
        line = line.trim();
        if (line.startsWith("#")) {
            return parseControl(line);
        }
        return parseMove(line);
    }

    private static Message parseMove(String s) {
        // 1Pe2e4 (6 caracteres) ou 1Pe7e8=Q (8 caracteres)
        if (s.length() != 6 && s.length() != 8) {
            throw new ProtocolException("Lance mal formado: " + s);
        }
        int player = s.charAt(0) - '0';
        char piece = s.charAt(1);
        ChessPosition source = parsePosition(s.substring(2, 4));
        ChessPosition target = parsePosition(s.substring(4, 6));
        Character promotion = null;
        if (s.length() == 8) {
            if (s.charAt(6) != '=') {
                throw new ProtocolException("Lance mal formado: " + s);
            }
            promotion = s.charAt(7);
        }
        return move(player, piece, source, target, promotion);
    }

    private static Message parseControl(String s) {
        int space = s.indexOf(' ');
        String keyword = (space < 0 ? s.substring(1) : s.substring(1, space)).toUpperCase();
        String arg = space < 0 ? "" : s.substring(space + 1).trim();
        Type type;
        try {
            type = Type.valueOf(keyword);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("Comando desconhecido: " + keyword);
        }
        return switch (type) {
            case ERRO -> erro(arg);
            case BEMVINDO -> bemVindo(parsePlayer(arg));
            case XEQUE -> xeque(parsePlayer(arg));
            case MATE -> mate(parsePlayer(arg));
            case SAIR -> sair(parsePlayer(arg));
            case MOVE -> throw new ProtocolException("Comando desconhecido: " + keyword);
        };
    }

    private static int parsePlayer(String arg) {
        if (arg.length() != 1) {
            throw new ProtocolException("Jogador inválido: " + arg);
        }
        return arg.charAt(0) - '0';
    }

    private static ChessPosition parsePosition(String s) {
        char column = Character.toLowerCase(s.charAt(0));
        int row = s.charAt(1) - '0';
        if (column < 'a' || column > 'h' || row < 1 || row > 8) {
            throw new ProtocolException("Posição inválida: " + s);
        }
        return new ChessPosition(column, row);
    }

    private static void checkPlayer(int player) {
        if (player != 1 && player != 2) {
            throw new ProtocolException("Jogador deve ser 1 ou 2, recebido: " + player);
        }
    }

    // ---------- mensagem -> texto ----------

    /** Texto exato que vai pelo socket (uma linha, sem o '\n'). */
    public String toWire() {
        return switch (type) {
            case MOVE -> "" + player + piece + source + target + (promotion == null ? "" : "=" + promotion);
            case ERRO -> text.isEmpty() ? "#ERRO" : "#ERRO " + text;
            default -> "#" + type.name() + " " + player;
        };
    }

    @Override
    public String toString() {
        return toWire();
    }

    // ---------- getters ----------

    public Type getType() { return type; }
    public int getPlayer() { return player; }
    public char getPiece() { return piece; }
    public ChessPosition getSource() { return source; }
    public ChessPosition getTarget() { return target; }
    public Character getPromotion() { return promotion; }
    public String getText() { return text; }
    public boolean isMove() { return type == Type.MOVE; }
}
