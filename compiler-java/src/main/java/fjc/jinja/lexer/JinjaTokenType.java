package fjc.jinja.lexer;

/**
 * Tokens of the combined HTML + Jinja template language. The lexer is
 * mode-based: HTML data, inside a start tag, inside an attribute value and
 * inside a Jinja expression each produce their own token kinds.
 */
public enum JinjaTokenType {
    // HTML data / structure
    TEXT,               // raw text between tags
    DOCTYPE,            // <!DOCTYPE html>
    HTML_COMMENT,       // <!-- ... -->
    TAG_OPEN,           // '<name' (text = tag name)
    TAG_END,            // '>'
    TAG_SELF_CLOSE,     // '/>'
    TAG_CLOSE,          // '</name>' (text = tag name)
    // inside a start tag
    ATTR_NAME,
    ATTR_EQ,            // '='
    ATTR_VALUE_START,   // opening quote
    ATTR_VALUE_END,     // closing quote
    ATTR_TEXT,          // literal text inside a quoted attribute value
    ATTR_UNQUOTED,      // unquoted attribute value
    // Jinja delimiters
    VAR_BEGIN,          // '{{'
    VAR_END,            // '}}'
    BLOCK_BEGIN,        // '{%'
    BLOCK_END,          // '%}'
    // Jinja expression tokens
    NAME,
    KEYWORD,            // for in endfor if elif else endif not and or is true false none
    STRING,
    NUMBER,
    OP,                 // == != <= >= < > + - * / // % ~ | ( ) [ ] , . : =
    EOF
}
