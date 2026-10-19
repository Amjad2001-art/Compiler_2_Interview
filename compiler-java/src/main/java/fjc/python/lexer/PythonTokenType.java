package fjc.python.lexer;

/** Token classes produced by {@link PythonLexer}. */
public enum PythonTokenType {
    NAME,       // identifiers
    KEYWORD,    // def, return, if, for, ... (see PythonLexer.KEYWORDS)
    NUMBER,     // integer or floating point literal (text kept verbatim)
    STRING,     // string literal (text = decoded value; raw kept separately)
    OP,         // operators and punctuation: ( ) [ ] { } , : . = == != < > <= >= + - * / // % @ += -= *= /=
    NEWLINE,    // end of a logical line
    INDENT,     // increase of indentation level
    DEDENT,     // decrease of indentation level
    EOF
}
