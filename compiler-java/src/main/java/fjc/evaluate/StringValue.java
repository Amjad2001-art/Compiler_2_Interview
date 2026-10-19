package fjc.evaluate;

/** Python str. {@code safe} marks HTML that must not be escaped again (Jinja's |safe). */
public final class StringValue extends Value {
    private final String value;
    private final boolean safe;

    public StringValue(String value) { this(value, false); }

    public StringValue(String value, boolean safe) {
        this.value = value;
        this.safe = safe;
    }

    public String value() { return value; }
    public boolean isSafe() { return safe; }

    @Override public String typeName() { return "str"; }
    @Override public boolean isTruthy() { return !value.isEmpty(); }
    @Override public String asText() { return value; }
    @Override public String repr() { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
    @Override public Object toJava() { return value; }
}
