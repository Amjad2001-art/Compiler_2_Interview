package fjc.common;

/**
 * A lexical token shared by all lexers. {@code type} is an enum constant of the
 * owning language (PythonTokenType, JinjaTokenType or CssTokenType).
 */
public final class Token {
    private final Enum<?> type;
    private final String text;
    private final SourcePosition position;

    public Token(Enum<?> type, String text, SourcePosition position) {
        this.type = type;
        this.text = text;
        this.position = position;
    }

    public Enum<?> type() { return type; }
    public String text() { return text; }
    public SourcePosition position() { return position; }
    public int line() { return position.line(); }
    public int column() { return position.column(); }

    public boolean is(Enum<?> t) { return type == t; }
    public boolean is(Enum<?> t, String value) { return type == t && text.equals(value); }

    @Override
    public String toString() {
        String shown = text.length() > 30 ? text.substring(0, 27) + "..." : text;
        return type.name() + "(" + shown.replace("\n", "\\n") + ")@" + position.line() + ":" + position.column();
    }
}
