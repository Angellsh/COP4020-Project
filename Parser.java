package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
//import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling those functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
        /**
         * Parses the {@code source} rule.
         */
    }

    public Ast.Source parseSource() throws ParseException {


        List<Ast.Field>  fields = new ArrayList<>();

        List<Ast.Method> methods = new ArrayList<>();

        while(match("LET")){
            fields.add(parseField());
        }
        while(match("DEF")){
            methods.add(parseMethod());
       }
        if(tokens.has(0)){
            throw new ParseException("Invalid syntax", handleIndex());}
        return new Ast.Source(fields, methods);

    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        //field ::= 'LET' 'CONST'? identifier ('=' expression)? ';'
        boolean constant = false;
        String typename =null;
        String name;
        if(match("CONST")) {
            constant = true;
        }
        if(match(Token.Type.IDENTIFIER)){
            name = tokens.get(-1).getLiteral();
        }
        else {
            throw new ParseException("IDENTIFIER is missing", handleIndex());
        }
        if(match(":")){
            if(match(Token.Type.IDENTIFIER)){
                typename = tokens.get(-1).getLiteral();
            }
            else throw new ParseException("Identifier is missing", handleIndex());

        }
        else {
            throw new ParseException("Type must be declared.", handleIndex());
        }

        if(match("=")){
            Ast.Expression expr = parseExpression();
            if (match(";") && typename!=null){
                return new Ast.Field(name, typename, constant, Optional.of(expr));

            }
        }
        if (match(";"))
            return new Ast.Field(name, typename, constant, Optional.empty());
        throw new ParseException("missing semicolon", handleIndex());
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
       // method ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' statement* 'END'

            //DEF name() DO stmt; END␊LET name = expr;
            List<Ast.Statement> stmnts = new ArrayList<>();
            List<String> params = new ArrayList<>();
            List<String> types = new ArrayList<>();


        String name;
        String returnType = null;

            if (match(Token.Type.IDENTIFIER)){
                name = tokens.get(-1).getLiteral();

                if (match("(")){
                    if(match(Token.Type.IDENTIFIER)){ //optional
                        params.add(tokens.get(-1).getLiteral());

                        if(match(":")){
                            if (match(Token.Type.IDENTIFIER)) {
                                types.add(tokens.get(-1).getLiteral());
                            }
                            else
                                throw new ParseException("Type is missing", handleIndex());

                        }
                        else
                            throw new ParseException("Type must be declared.", handleIndex());

                        while(match(",") && match(Token.Type.IDENTIFIER)){
                            params.add(tokens.get(-1).getLiteral());
                            if(match(":")){
                                if (match(Token.Type.IDENTIFIER)) {
                                    types.add(tokens.get(-1).getLiteral());
                                }
                                else
                                    throw new ParseException("Type is missing", handleIndex());

                            }
                        }
                    }
                    if(!match(")")){
                        throw new ParseException("Must have a closing ')'",handleIndex());
                    }
                }
                else
                    throw new ParseException("Must have an opening ')' ", handleIndex());

            }
            else
                throw new ParseException("Method name is missing", handleIndex());
            if(match((":"))){
                if (match(Token.Type.IDENTIFIER))
                    returnType = tokens.get(-1).getLiteral();
            }

            if (match("DO")){
                while (!match("END") && tokens.has(0))
                    stmnts.add(parseStatement());
                if(returnType == null)
                    return new Ast.Method(name, params, types, Optional.empty(), stmnts);
                else
                    return new Ast.Method(name, params, types, Optional.of(returnType), stmnts);

            }
            else
                throw new ParseException("'DO' is missing", handleIndex());

    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, for, while, or return
     * statement, then it is an expression/assignment statement.
     *
     */
    public int handleIndex() throws ParseException  {
        if(tokens.has(0)){
            return tokens.get(0).getIndex();
        }
        return tokens.get(-1).getIndex();
    }
    public Ast.Statement parseStatement() throws ParseException {
        // name = val;
        //   new Ast.Statement.Assignment(
        //   new Ast.Expression.Access(Optional.empty(), "name"),
        //    new Ast.Expression.Access(Optional.empty(), "value"))
        //      first name is evaluated and passed as a received when
        //
        //assignment case
            if (peek(Token.Type.IDENTIFIER) && match("LET")) {
                return parseDeclarationStatement();
            } else if (peek(Token.Type.IDENTIFIER) && match("IF")) {
                return parseIfStatement();
            } else if (peek(Token.Type.IDENTIFIER) && match("FOR")) {
                return parseForStatement();
            } else if (peek(Token.Type.IDENTIFIER) && match("WHILE")) {
                return parseWhileStatement();
            } else if (peek(Token.Type.IDENTIFIER) && match("RETURN")) {
                return parseReturnStatement();
            }
            Ast.Expression left = parseExpression();

        if (match("=")) {

            Ast.Expression right = parseExpression();
                if (match(";"))
                    return new Ast.Statement.Assignment(left, right);
                else
                    throw new ParseException("No terminator.", handleIndex());
            }

        else if (match(";")) {
            return new Ast.Statement.Expression(left);
        }

        throw new ParseException("Invalid statement.", handleIndex());




    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
       // 'LET' identifier ('=' expression)? ';' |
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();
            String typename = null;
            if(match(":")){
                if (match(Token.Type.IDENTIFIER)){
                    typename= tokens.get(-1).getLiteral();
                }
                else
                    throw new ParseException("Type is missing", handleIndex());

            }
            if(match("=")) {
                Ast.Expression right = parseExpression();
                if (match(";"))
                    if (typename!=null)
                        return new Ast.Statement.Declaration(name, Optional.of(typename), Optional.of(right));
                    else
                        return new Ast.Statement.Declaration(name, Optional.empty(), Optional.of(right)); }
            if (match(";"))
                if (typename!=null)
                    return new Ast.Statement.Declaration(name, Optional.of(typename), Optional.empty());
                else
                    return new Ast.Statement.Declaration(name, Optional.empty(), Optional.empty());


        }
        throw new ParseException("Invalid declaration.", handleIndex());
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
      //  'IF' expression 'DO' statement* ('ELSE' statement*)? 'END' |

        Ast.Expression expr = parseExpression();
        if (match("DO")){

            List<Ast.Statement> statements = new ArrayList<>();
            List<Ast.Statement> elseStatements = new ArrayList<>();

            while(! peek("END") && !peek("ELSE")){
                statements.add(parseStatement());
            }
            if(match("ELSE")){
                while(!peek("END")) {
                    elseStatements.add(parseStatement());
                }
            }
            if (match("END")) {

                return new Ast.Statement.If(expr, statements, elseStatements);
            }
            throw new ParseException("Invalid if statement." ,handleIndex());
        }
        throw new ParseException("Invalid if statement." , handleIndex());
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Statement.For parseForStatement() throws ParseException {
        //'FOR' '(' (identifier '=' expression)? ';' expression ';' (identifier '=' expression)? ')' statement* 'END' |
        String id ="";
        Ast.Statement st1 = null;
        Ast.Statement st2 = null;

        if (match("(")){
            if(match(Token.Type.IDENTIFIER)){
                id =  tokens.get(-1).getLiteral();
                if(match("=")){
                    Ast.Expression expr1 = parseExpression();
                     st1 = new Ast.Statement.Declaration(id, Optional.of(expr1));
                }
                else {
                    throw new ParseException("= missing", handleIndex());
                }
            }
            else {
                throw new ParseException("Missing identifier", handleIndex());
            }

        }
        if(!match(";")){
            throw new ParseException("Missing semicolon", handleIndex());
        }
        Ast.Expression expr2 = parseExpression();
        if(!match(";")){
            throw new ParseException("Missing semicolon", handleIndex());
        }
        if(match(Token.Type.IDENTIFIER)) {
            String id2 = tokens.get(-1).getLiteral();
            if (!match("=")) {
                throw new ParseException("Missing equal sign", handleIndex());
            }
            Ast.Expression expr3 = parseExpression();
            st2 = new Ast.Statement.Declaration(id2, Optional.of(expr3));
            if (!match(")")) {
                throw new ParseException("Missing semicolon", handleIndex());
            }
        }
        List<Ast.Statement> stmnts = new ArrayList<>();
        while(!match("END") ){
            if(!tokens.has(0))
                throw new ParseException("EOI", handleIndex());
            stmnts.add(parseStatement());
        }

        if(st1!=null){
            return new Ast.Statement.For(st1, expr2, st2, stmnts);
        }
        return new Ast.Statement.For(st1, expr2, st2, stmnts); //handle properly
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        //    'WHILE' expression 'DO' statement* 'END' |
        Ast.Expression expr = parseExpression();

        //
         //new Ast.Statement.While(
        // new Ast.Expression.Access(Optional.empty(), "expr"),
        //  Arrays.asList(new Ast.Statement.Expression(new Ast.Expression.Access(Optional.empty(), "stmt")))
        // )
        // )
        if (match("DO")){
            List<Ast.Statement> statements = new ArrayList<>();
            while (! match("END")){
                Ast.Expression.Statement stmt =   parseStatement();
                statements.add(stmt);
            }
            return new Ast.Statement.While(expr, statements );

        }
        throw new ParseException("Invalid statement.", handleIndex());

    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        return new Ast.Statement.Return(parseExpression());
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        Ast.Expression expr =  parseLogicalExpression();
       return expr;
    }

    /**
     * Parses the {@code logical-expression} rule.
     */

    public Ast.Expression parseLogicalExpression() throws ParseException {

        Ast.Expression left = parseEqualityExpression();

        while (match("OR") || match("AND"))  {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression right = parseEqualityExpression();
            left = new Ast.Expression.Binary(operator, left, right);
            }

        return left;
    }


    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseEqualityExpression() throws ParseException {


        Ast.Expression expr = parseAdditiveExpression();
        while (match("<") || match("<=") || match(">")
                || match(">=") || match("==") || match("!=")) {
                String operator = tokens.get(-1).getLiteral();
                Ast.Expression right = parseAdditiveExpression();
                expr = new Ast.Expression.Binary(operator, expr, right);
            }
            return expr;

    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {

        Ast.Expression expr = parseMultiplicativeExpression();
        while (match("+") || match("-") ){
            String operator = tokens.get(-1).getLiteral();
            Ast.Expression expr2 = parseMultiplicativeExpression();
            expr = new Ast.Expression.Binary(operator, expr, expr2 );
        }
        return expr;


    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
            Ast.Expression expr = parseSecondaryExpression();
            while (match("*") || match("/")) {
                String operator = tokens.get(-1).getLiteral();
                Ast.Expression expr2 = parseSecondaryExpression();
                expr = new Ast.Expression.Binary(operator, expr, expr2);
            }

        return expr;

    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expression parseSecondaryExpression() throws ParseException {
        //1. expr = Ast.Expression.Access(Optional.empty(), obj)
        //2. Ast.Expression.Access( Ast.Expression.Access(Optional.empty(), obj), func)
        // Ast.Expression.Function(Ast.Expression.Access(Optional.of(new Ast.Expression.Access(Optional.empty(), "obj")), "func"), func, [] )
        //
            Ast.Expression expr = parsePrimaryExpression();

        while (match(".")) {



            if (match(Token.Type.IDENTIFIER)) {
                String name = tokens.get(-1).getLiteral();

                if (peek("(")) {
                    expr = parseFunctionHelper(name, Optional.of(expr));
                }
                else{
                        expr = new Ast.Expression.Access(Optional.of(expr), name);

                }

            }
            else
                throw new ParseException("Invalid expression.", handleIndex());
        }
        return expr;


    }

    public Ast.Expression parseFunctionHelper(String name, Optional<Ast.Expression> expr) throws ParseException {
        //access

            List<Ast.Expression> lst = new ArrayList<>();

            if (match("(")) {
                if(match(")")){
                    if (expr.isPresent()) {
                        Ast.Expression expr_object = expr.get();
                        return new Ast.Expression.Function(Optional.of(expr_object), name,  lst );
                    }
                    else if(expr.isEmpty()){
                        return new Ast.Expression.Function(Optional.empty(), name,  lst );

                    }
                }
                Ast.Expression expr2 = parseExpression();
                lst.add(expr2);

                while (match(",")) {
                        expr2 = parseExpression();
                        lst.add(expr2);
                }
                if (!match(")")) {
                    throw new ParseException("Must have a closing \"(", handleIndex());
                }
                if (expr.isPresent()) {
                    Ast.Expression expr_object = expr.get();
                    return new Ast.Expression.Function(Optional.of(expr_object), name, lst);

                }
                else{
                    return new Ast.Expression.Function(Optional.empty(), name, lst);

                }
            }

            throw new ParseException("Invalid parameters", handleIndex());


    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     *
     */

    Ast.Expression.Literal CharactersIterator(String word, String finalstr,  boolean chr){
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == '\\') {
                switch(word.charAt(i+1)){
                    case 'n' ->  finalstr = finalstr + '\n';
                    case 'r' ->  finalstr = finalstr + '\r';
                    case 't' ->  finalstr = finalstr + '\t';
                    case 'f' -> finalstr = finalstr + '\f';
                    case '\\' -> finalstr = finalstr + '\\';
                    case '"' -> finalstr = finalstr + '"';
                    default -> finalstr = finalstr + word.charAt(i+1);
                }

                i+=1;

            }
            else
                finalstr += word.charAt(i);

        }
        if (chr)
            return new Ast.Expression.Literal(finalstr.charAt(finalstr.length()-1));

        return new Ast.Expression.Literal(finalstr);
    }



    public Ast.Expression parsePrimaryExpression() throws ParseException {
        //identifier

            if (match("TRUE"))
            {

                return new Ast.Expression.Literal(true);
            } else if (match("FALSE")) {
                return new Ast.Expression.Literal(false);
            } else if (match("NIL")) {
                return new Ast.Expression.Literal(null);
            } else if (match(Token.Type.INTEGER)) {
                return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
            } else if (match(Token.Type.DECIMAL)) {
                return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
            } else if (match(Token.Type.CHARACTER)) {

                String str = tokens.get(-1).getLiteral();
                str = str.substring(1, str.length()-1);
                if(str.length() == 1) {
                    char c = str.charAt(0);
                    return new Ast.Expression.Literal(c);
                }
                String finalstr = "";
                return CharactersIterator(str, finalstr, true);








            } else if (match(Token.Type.STRING)) {
                String str = tokens.get(-1).getLiteral();
                String word = str.substring(1, str.length() - 1);
                String finalstr = "";
                return CharactersIterator(word, finalstr, false);
                //return new Ast.Expression.Literal(str);

            } else if (match("(")) {
                Ast.Expression expr = parseExpression();
                if (!match(")"))
                    throw new ParseException("Missing ')'", handleIndex());
                return new Ast.Expression.Group(expr);
            } else if (match(Token.Type.IDENTIFIER)) {
                String name = tokens.get(-1).getLiteral();
                //a function without receiver
                if(peek("(")) {

                    return parseFunctionHelper(name, Optional.empty());
                }
                else
                    return new Ast.Expression.Access(Optional.empty(), name);

            } else {
                throw new ParseException("Unknown expression", handleIndex());
            }


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
                }
                else if (patterns[i] instanceof Token.Type) {
                    if (patterns[i] != tokens.get(i).getType()) {
                        return false;
                    }
                }
                else if (patterns[i] instanceof String) {
                   if  (!patterns[i].equals(tokens.get(i).getLiteral())) {
                        return false;

                    }

                }
                else
                    throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());

            }
            return true;


    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        if (peek(patterns) ){
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
            return true;
        }
        return false;
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
