package fjc.common;

/**
 * Thrown by lexers and parsers for fatal lexical/syntax errors. The pipeline
 * converts it into a {@link CompilerError}, so bad input never crashes the
 * compiler: it is always reported with file, line and column.
 */
public class CompilerException extends RuntimeException {
    private final CompilerError error;

    public CompilerException(CompilerError error) {
        super(error.format());
        this.error = error;
    }

    public CompilerException(ErrorCategory category, String code, String name, String message, SourcePosition pos) {
        this(CompilerError.error(category, code, name, message, pos));
    }

    public CompilerError error() { return error; }
}
