package fjc.evaluate;

import java.util.List;
import java.util.Map;

/** A function implemented in Java (len, int, render_template, url_for, ...). */
public final class BuiltinFunction extends Value {
    @FunctionalInterface
    public interface Body {
        Value call(List<Value> args, Map<String, Value> kwargs);
    }

    private final String name;
    private final Body body;

    public BuiltinFunction(String name, Body body) {
        this.name = name;
        this.body = body;
    }

    public String name() { return name; }
    public Value call(List<Value> args, Map<String, Value> kwargs) { return body.call(args, kwargs); }

    @Override public String typeName() { return "builtin_function"; }
    @Override public boolean isTruthy() { return true; }
    @Override public String asText() { return "<built-in function " + name + ">"; }
    @Override public Object toJava() { return asText(); }
}
