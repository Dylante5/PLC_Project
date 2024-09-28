package plc.project;

import java.util.ArrayList;
import java.util.List;

public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        while (chars.has(0)) {
            if (peek("[ \n\r\t]")) {
                match("[ \n\r\t]"); // skip valid whitespace characters
            } else {
                tokens.add(lexToken());
            }
        }
        return tokens;
    }

    public Token lexToken() {
        if (peek("[A-Za-z_]")) {
            return lexIdentifier();
        } else if (peek("[+-]?", "[0-9]")) {
            return lexNumber();
        } else if (peek("'")) {
            return lexCharacter();
        } else if (peek("\"")) {
            return lexString();
        } else if (peek("[<>!=]=", ".")) {
            return lexOperator();
        } else if (peek(".")) {
            return lexOperator();
        } else {
            throw new ParseException("Invalid token", chars.getIndex());
        }
    }

    public Token lexIdentifier() {
        match("[A-Za-z_]");
        while (match("[A-Za-z0-9_-]"));
        return new Token(Token.Type.IDENTIFIER, chars.emit(), chars.getIndex());
    }

    public Token lexNumber() {
        match("[+-]?");
        while (match("[0-9]"));
        if (match("\\.")) {
            while (match("[0-9]"));
            return new Token(Token.Type.DECIMAL, chars.emit(), chars.getIndex());
        } else {
            return new Token(Token.Type.INTEGER, chars.emit(), chars.getIndex());
        }
    }

    public Token lexCharacter() {
        match("'"); // Match the opening single quote
        if (peek("[^'\\n\\r\\\\]")) {
            // match a single character that is not a quote, newline, or backslash
            match("[^'\\n\\r\\\\]");
        } else if (peek("\\\\")) {
            // match an escape sequence
            lexEscape();
        } else {
            throw new ParseException("Invalid character literal", chars.getIndex());
        }

        // after matching a valid character, ensure the closing single quote is present
        if (!match("'")) {
            throw new ParseException("Unterminated character literal", chars.getIndex());
        }

        return new Token(Token.Type.CHARACTER, chars.emit(), chars.getIndex());
    }

    public Token lexString() {
        match("\"");
        while (peek("[^\\" + "\"\\n\\r\\\\]") || peek("\\\\")) {
            if (peek("\\\\")) lexEscape();
            else match(".");
        }
        if (!match("\"")) {
            throw new ParseException("Unterminated string", chars.getIndex());
        }
        return new Token(Token.Type.STRING, chars.emit(), chars.getIndex());
    }

    public Token lexOperator() {
        match(".");
        return new Token(Token.Type.OPERATOR, chars.emit(), chars.getIndex() - 1);
    }

    public void lexEscape() {
        if (match("\\\\")) {
            // check for valid escape characters after the backslash
            if (!match("[bnrt'\"\\\\]")) {
                throw new ParseException("Invalid escape sequence", chars.getIndex());
            }
        } else {
            throw new ParseException("Expected escape sequence", chars.getIndex());
        }
    }

    private boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean match(String... patterns) {
        boolean matches = peek(patterns);
        if (matches) {
            for (String pattern : patterns) {
                chars.advance();
            }
        }
        return matches;
    }

    private static final class CharStream {
        private final String input;
        private int index = 0;
        private int start = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
        }

        public String emit() {
            String token = input.substring(start, index);
            start = index;
            return token;
        }

        public int getIndex() {
            return index;
        }
    }
}
