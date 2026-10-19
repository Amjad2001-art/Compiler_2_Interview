package fjc.evaluate;

/**
 * A name imported from a module the compiler does not model (e.g. the runtime
 * helper {@code regenerate.py}). Calling it does nothing and returns None;
 * attribute access yields another external value. It is never part of the
 * GET rendering path that the generator needs.
 */
public final class ExternalValue extends Value {
    private final String name;

    public ExternalValue(String name) { this.name = name; }

    public String name() { return name; }

    @Override public String typeName() { return "external"; }
    @Override public boolean isTruthy() { return true; }
    @Override public String asText() { return "<external " + name + ">"; }
    @Override public Object toJava() { return asText(); }
}
