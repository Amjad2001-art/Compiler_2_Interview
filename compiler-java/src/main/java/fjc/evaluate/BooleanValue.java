package fjc.evaluate;

/** Python {@code True} / {@code False}. */
public final class BooleanValue extends Value {
    public static final BooleanValue TRUE = new BooleanValue(true);
    public static final BooleanValue FALSE = new BooleanValue(false);

    private final boolean value;

    private BooleanValue(boolean value) { this.value = value; }

    public static BooleanValue of(boolean b) { return b ? TRUE : FALSE; }

    public boolean value() { return value; }

    @Override public String typeName() { return "bool"; }
    @Override public boolean isTruthy() { return value; }
    @Override public String asText() { return value ? "True" : "False"; }
    @Override public Object toJava() { return value; }
}
