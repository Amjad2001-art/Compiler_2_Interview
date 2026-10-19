package fjc.evaluate;

/** Python {@code None}. */
public final class NoneValue extends Value {
    public static final NoneValue NONE = new NoneValue();

    private NoneValue() {}

    @Override public String typeName() { return "NoneType"; }
    @Override public boolean isTruthy() { return false; }
    @Override public String asText() { return "None"; }
    @Override public Object toJava() { return null; }
    @Override public boolean isNone() { return true; }
}
