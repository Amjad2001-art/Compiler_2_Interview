package fjc.css;

/** Token classes produced by {@link CssLexer}. */
public enum CssTokenType {
    IDENT,        // identifiers and property names (including --custom-properties)
    AT_KEYWORD,   // @media, @keyframes, ...
    HASH,         // #fff, #id
    STRING,       // "..." or '...'
    NUMBER,       // 12, .5, 1.5e3
    DIMENSION,    // 12px, 1.5rem, 100vh
    PERCENTAGE,   // 50%
    FUNCTION,     // ident followed by '(' e.g. var( rgba( url(
    URL,          // url(unquoted)
    COLON, SEMICOLON, COMMA,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET,
    DELIM,        // any other single character (* > + ~ . = ! / etc.)
    EOF
}
