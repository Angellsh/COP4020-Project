package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the invalid character.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation easier.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */

    public List<Token> lex() {
        List<Token> tokens  = new ArrayList<>();
        while(chars.has(0)){

            if(peek("[\b]|\\r|\\n|\\t|\\s")){
                chars.advance();
                chars.skip();
            }

            else {
                Token token = lexToken();
                tokens.add(token);
            }
        }

        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {

        if(peek("[A-Za-z_]")){
          return lexIdentifier();
      }
        if (peek("[0-9]")){
            return lexNumber();
        }
        if(peek("[+-]")){
            match("[+-]");

            if (peek("[0-9]")){
                return lexNumber();

            }
            return chars.emit(Token.Type.OPERATOR); //fix later

        }

      if(peek("'")){
          match("'");

          return lexCharacter();
      }
      if (peek("\"")){
          return lexString();
        }
      if(peek("\\\\")){
          lexEscape();
      }
        return lexOperator();


    }

    public Token lexIdentifier() {
        match("[A-Za-z_]");

        while(peek("[A-Za-z0-9_-]")){
            match("[A-Za-z0-9_-]|");
        }
        return chars.emit(Token.Type.IDENTIFIER);

    }


    public Token lexNumber() {
        if (peek("0")) {
            match("0");
            if (peek("\\.")) {
                return lexDecimal();

            }
            else if(peek("[^0-9]"))  {
                return chars.emit(Token.Type.INTEGER);
            }
            else
                throw new ParseException("Invalid character", chars.index);

        }
        if (peek("[1-9]")) {
            match("[1-9]");
            while(peek("[0-9]")) {
                match("[0-9]");
            }
            if(peek("\\.")){
                return lexDecimal();
            }
            return chars.emit(Token.Type.INTEGER);

        }
            throw new ParseException("Invalid character", chars.index);


    }


    public Token lexDecimal() {
        if (peek("\\.")) {
            match("\\.");

            if (peek("[0-9]")) {
                while (peek("[0-9]")) {
                    match("[0-9]");
                }
                return chars.emit(Token.Type.DECIMAL);
            }
          throw new ParseException("Invalid character", chars.index);
        }

        throw new ParseException("Invalid character", chars.index);
    }

    public Token lexCharacter() {

        if(peek("\\\\")) {
            match("\\\\");
            if (peek("[bnrt'\\\"]|(\\\\)")) {
                match("[bnrt'\\\"]|(\\\\)");
                if (peek("'")) {
                    match("'");
                    return chars.emit(Token.Type.CHARACTER);
                } else
                    throw new ParseException("Invalid character", chars.index);

            }
            throw new ParseException("Invalid character", chars.index);
        }


        if(peek(".")){

            match(".");
            if(peek("'")){
                match("'");
                return chars.emit(Token.Type.CHARACTER);
            }
            throw new ParseException("Invalid characters", chars.index);
        }
        throw new ParseException("Invalid characters", chars.index);
    }

    public Token lexString() {
        match("\\\"");

        while(true) {
            if (peek("\\\"")){ //literal \"
                match("\\\"");
                return chars.emit(Token.Type.STRING);
            }
            if (peek("[\\n\\r]")){
                throw new ParseException("Invalid string", chars.index);
            }

            if(peek("\\\\")){ // runtime "\\"
                match("\\\\");
                if(peek("[bnrt'\\\"]|(\\\\)")){
                    match("[bnrt'\\\"]|(\\\\)");
                }
                else
                    throw new ParseException("Invalid characters", chars.index);

            }

            else if (peek("\\b|\\n|\\r|\\t")) {
                match("\\b|\\n|\\r|\\t");
            }

            else if (peek("[^\"\n\r\\\\]")){
                match("[^\"\n\r\\\\]");
            }
            else
                break;
        }
        throw new ParseException("Invalid characters", chars.index);
    }

    public void lexEscape() {
        match("\\\\");

        if (peek("[bnrt'\"\\\\]")){
            match("[bnrt'\"\\\\]");
        }
        else
            throw new ParseException("Invalid escape character", chars.index);

    }

    public Token lexOperator() {
        if (peek("\\.")){
            match("\\.");
            return chars.emit(Token.Type.OPERATOR);
        }

        if(peek("[<>!=]")){
            match("[<>!=]");
            if (peek("=")){
                match("=");
                return chars.emit(Token.Type.OPERATOR);
            }

            return chars.emit(Token.Type.OPERATOR);
        }
        if(peek(".")){
            match(".");
            return chars.emit(Token.Type.OPERATOR);
        }
       throw new ParseException("Invalid characters", chars.index);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i=0; i<patterns.length; i++){
            if(!chars.has(i)|| !String.valueOf(chars.get(i)).matches(patterns[i])){
                return false;
            }

        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        if (peek(patterns)){
            for (int i=0; i <patterns.length;i++){
                chars.advance();

            }
            return true;
        }
        return false;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

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
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
