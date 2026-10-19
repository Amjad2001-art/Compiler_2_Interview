package fjc.common;

/**
 * A single diagnostic: category, code, name, severity, message and position.
 * Printable in a stable one-line format, e.g.
 * <pre>[PY001] ERROR app.py:12:5 UndefinedVariable: name 'x' is not defined</pre>
 */
public final class CompilerError {
    private final ErrorCategory category;
    private final String code;
    private final String name;
    private final Severity severity;
    private final String message;
    private final SourcePosition position;

    public CompilerError(ErrorCategory category, String code, String name, Severity severity,
                         String message, SourcePosition position) {
        this.category = category;
        this.code = code;
        this.name = name;
        this.severity = severity;
        this.message = message;
        this.position = position == null ? SourcePosition.unknown("<unknown>") : position;
    }

    public static CompilerError error(ErrorCategory category, String code, String name,
                                      String message, SourcePosition position) {
        return new CompilerError(category, code, name, Severity.ERROR, message, position);
    }

    public static CompilerError warning(ErrorCategory category, String code, String name,
                                        String message, SourcePosition position) {
        return new CompilerError(category, code, name, Severity.WARNING, message, position);
    }

    public ErrorCategory category() { return category; }
    public String code() { return code; }
    public String name() { return name; }
    public Severity severity() { return severity; }
    public String message() { return message; }
    public SourcePosition position() { return position; }
    public String fileName() { return position.fileName(); }
    public int line() { return position.line(); }
    public int column() { return position.column(); }
    public boolean isError() { return severity == Severity.ERROR; }

    public String format() {
        return "[" + code + "] " + severity + " " + position + " " + name + ": " + message;
    }

    @Override
    public String toString() { return format(); }
}
