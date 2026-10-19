package fjc.evaluate;

/** Python int / float. Integers are kept exact as long values. */
public final class NumberValue extends Value {
    private final long longValue;
    private final double doubleValue;
    private final boolean integer;

    private NumberValue(long l, double d, boolean integer) {
        this.longValue = l;
        this.doubleValue = d;
        this.integer = integer;
    }

    public static NumberValue ofLong(long v) { return new NumberValue(v, v, true); }
    public static NumberValue ofDouble(double v) { return new NumberValue((long) v, v, false); }

    public boolean isInteger() { return integer; }
    public long longValue() { return integer ? longValue : (long) doubleValue; }
    public double doubleValue() { return doubleValue; }

    @Override public String typeName() { return integer ? "int" : "float"; }
    @Override public boolean isTruthy() { return doubleValue != 0; }
    @Override public String asText() { return integer ? Long.toString(longValue) : formatDouble(doubleValue); }
    @Override public Object toJava() { return integer ? (Object) longValue : (Object) doubleValue; }

    /** Python-like float formatting: 300.0 stays "300.0", 129.99 stays "129.99". */
    public static String formatDouble(double d) {
        if (Double.isNaN(d)) return "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        if (d == Math.rint(d) && Math.abs(d) < 1e16) return String.format(java.util.Locale.ROOT, "%.1f", d);
        String s = Double.toString(d);
        if (s.contains("E")) {
            java.math.BigDecimal bd = new java.math.BigDecimal(d).stripTrailingZeros();
            return bd.toPlainString();
        }
        return s;
    }
}
