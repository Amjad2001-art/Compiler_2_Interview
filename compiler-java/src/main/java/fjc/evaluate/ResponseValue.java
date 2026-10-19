package fjc.evaluate;

/** What a route returns: a rendered template, a redirect or another response. */
public final class ResponseValue extends Value {
    public enum Kind { RENDER, REDIRECT, OTHER }

    private final Kind kind;
    private final String detail;

    public ResponseValue(Kind kind, String detail) {
        this.kind = kind;
        this.detail = detail;
    }

    public Kind kind() { return kind; }
    public String detail() { return detail; }

    @Override public String typeName() { return "Response"; }
    @Override public boolean isTruthy() { return true; }
    @Override public String asText() { return "<Response " + kind.name().toLowerCase() + " " + detail + ">"; }
    @Override public Object toJava() { return asText(); }
}
