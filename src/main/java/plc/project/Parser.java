package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 * <p>
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 * <p>
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        // Loop to parse fields and methods until we run out of tokens.
        while (match("LET")) {
            fields.add(parseField());
        }
        while (match("DEF")) {
            methods.add(parseMethod());
        }

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        if (!match("LET")) {
            throw new ParseException("Expected 'LET' at the start of a field declaration.", tokens.get(0).getIndex());
        }

        // Parsing identifier directly inside this method
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after 'LET'.", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();

        Ast.Expr initializer = null;
        if (match("=")) {
            initializer = parseExpression();
        }

        if (!match(";")) {
            throw new ParseException("Expected ';' at the end of a field declaration.", tokens.get(0).getIndex());
        }

        return new Ast.Field(name, Optional.ofNullable(initializer));
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        if (!match("DEF")) {
            throw new ParseException("Expected 'DEF' at the start of a method declaration.", tokens.get(0).getIndex());
        }

        // Parsing identifier directly in this method
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected method name after 'DEF'.", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();

        if (!match("(")) {
            throw new ParseException("Expected '(' after method name.", tokens.get(0).getIndex());
        }

        List<String> parameters = new ArrayList<>();
        if (!match(")")) {
            do {
                if (!match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected parameter name.", tokens.get(0).getIndex());
                }
                parameters.add(tokens.get(-1).getLiteral());
            } while (match(","));
            if (!match(")")) {
                throw new ParseException("Expected ')' after parameters.", tokens.get(0).getIndex());
            }
        }

        if (!match("DO")) {
            throw new ParseException("Expected 'DO' after method parameters.", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (!match("END")) {
            statements.add(parseStatement());
        }

        return new Ast.Method(name, parameters, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if (match("LET")) {
            return parseDeclarationStatement();
        } else if (match("IF")) {
            return parseIfStatement();
        } else if (match("WHILE")) {
            return parseWhileStatement();
        } else if (match("RETURN")) {
            return parseReturnStatement();
        } else {
            // If none of the above, it must be an expression/assignment statement
            Ast.Expr expr = parseExpression();
            if (match("=")) {
                Ast.Expr value = parseExpression();
                if (!match(";")) {
                    throw new ParseException("Expected ';' after assignment.", tokens.get(0).getIndex());
                }
                return new Ast.Stmt.Assignment(expr, value);
            }
            if (!match(";")) {
                throw new ParseException("Expected ';' after expression.", tokens.get(0).getIndex());
            }
            return new Ast.Stmt.Expression(expr);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        if (!match("LET")) {
            throw new ParseException("Expected 'LET' at the start of a declaration statement.", tokens.get(0).getIndex());
        }

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after 'LET'.", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();

        Ast.Expr initializer = null;
        if (match("=")) {
            initializer = parseExpression();
        }

        if (!match(";")) {
            throw new ParseException("Expected ';' after declaration.", tokens.get(0).getIndex());
        }

        return new Ast.Stmt.Declaration(name, Optional.ofNullable(initializer));
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        if (!match("IF")) {
            throw new ParseException("Expected 'IF' at the start of an if statement.", tokens.get(0).getIndex());
        }

        Ast.Expr condition = parseExpression();

        if (!match("DO")) {
            throw new ParseException("Expected 'DO' after condition in if statement.", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> thenStatements = new ArrayList<>();
        while (!peek("ELSE", "END")) {
            thenStatements.add(parseStatement());
        }

        List<Ast.Stmt> elseStatements = new ArrayList<>();
        if (match("ELSE")) {
            while (!match("END")) {
                elseStatements.add(parseStatement());
            }
        } else {
            if (!match("END")) {
                throw new ParseException("Expected 'END' to terminate the if statement.", tokens.get(0).getIndex());
            }
        }

        return new Ast.Stmt.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        if (!match("FOR")) {
            throw new ParseException("Expected 'FOR' at the start of a for loop.", tokens.get(0).getIndex());
        }

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after 'FOR'.", tokens.get(0).getIndex());
        }
        String name = tokens.get(-1).getLiteral();

        if (!match("IN")) {
            throw new ParseException("Expected 'IN' after identifier in for loop.", tokens.get(0).getIndex());
        }

        Ast.Expr value = parseExpression();

        if (!match("DO")) {
            throw new ParseException("Expected 'DO' after range in for loop.", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (!match("END")) {
            statements.add(parseStatement());
        }

        return new Ast.Stmt.For(name, value, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        if (!match("WHILE")) {
            throw new ParseException("Expected 'WHILE' at the start of a while loop.", tokens.get(0).getIndex());
        }

        Ast.Expr condition = parseExpression();

        if (!match("DO")) {
            throw new ParseException("Expected 'DO' after condition in while loop.", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (!match("END")) {
            statements.add(parseStatement());
        }

        return new Ast.Stmt.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        if (!match("RETURN")) {
            throw new ParseException("Expected 'RETURN' at the start of a return statement.", tokens.get(0).getIndex());
        }

        Ast.Expr value = parseExpression();

        if (!match(";")) {
            throw new ParseException("Expected ';' after return statement.", tokens.get(0).getIndex());
        }

        return new Ast.Stmt.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        Ast.Expr expr = parseEqualityExpression();
        while (match("AND", "OR")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseEqualityExpression();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }
        return expr;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        Ast.Expr expr = parseAdditiveExpression();
        while (match("==", "!=", "<", "<=", ">", ">=")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseAdditiveExpression();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }
        return expr;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        Ast.Expr expr = parseMultiplicativeExpression();
        while (match("+", "-")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseMultiplicativeExpression();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }
        return expr;
    }
    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr expr = parseSecondaryExpression();
        while (match("*", "/")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseSecondaryExpression();
            expr = new Ast.Expr.Binary(operator, expr, right);
        }
        return expr;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {
        Ast.Expr expr = parsePrimaryExpression();
        while (match(".")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier after '.'.", tokens.get(0).getIndex());
            }
            String name = tokens.get(-1).getLiteral();

            // Check if this is a method call or just field access
            if (match("(")) {
                // Parse arguments for the function/method call
                List<Ast.Expr> arguments = new ArrayList<>();
                if (!match(")")) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(","));
                    if (!match(")")) {
                        throw new ParseException("Expected ')' after arguments.", tokens.get(0).getIndex());
                    }
                }
                expr = new Ast.Expr.Function(Optional.of(expr), name, arguments);  // Method call with a receiver
            } else {
                expr = new Ast.Expr.Access(Optional.of(expr), name);  // Just field access
            }
        }
        return expr;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if (match("TRUE") || match("true")) {  // Check for both upper and lowercase 'true'
            return new Ast.Expr.Literal(true);
        } else if (match("FALSE") || match("false")) {  // Check for both upper and lowercase 'false'
            return new Ast.Expr.Literal(false);
        } else if (match(Token.Type.INTEGER)) {
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.CHARACTER)) {
            String literal = tokens.get(-1).getLiteral();
            char character = literal.charAt(1);  // Remove surrounding quotes
            return new Ast.Expr.Literal(character);
        } else if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            literal = literal.substring(1, literal.length() - 1);  // Remove surrounding quotes
            literal = literal.replace("\\n", "\n").replace("\\t", "\t");  // Handle common escape characters
            return new Ast.Expr.Literal(literal);
        } else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();

            // Check if this is a function call
            if (match("(")) {
                List<Ast.Expr> arguments = new ArrayList<>();
                if (!match(")")) {  // If it's not an empty argument list
                    do {
                        arguments.add(parseExpression());  // Parse each argument
                    } while (match(","));  // Separate arguments by commas
                    if (!match(")")) {
                        throw new ParseException("Expected ')' after arguments.", tokens.get(0).getIndex());
                    }
                }
                return new Ast.Expr.Function(Optional.empty(), name, arguments);  // Global function call
            }

            // If it's not a function call, return it as an identifier access
            return new Ast.Expr.Access(Optional.empty(), name);
        } else if (match("(")) {
            // Handle grouped expressions (e.g., "(1 + 2)")
            Ast.Expr expr = parseExpression();
            if (!match(")")) {
                throw new ParseException("Expected ')' after expression.", tokens.get(0).getIndex());
            }
            return new Ast.Expr.Group(expr);
        }

        // If none of the conditions match, throw an error
        throw new ParseException("Expected a primary expression.", tokens.get(0).getIndex());
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
