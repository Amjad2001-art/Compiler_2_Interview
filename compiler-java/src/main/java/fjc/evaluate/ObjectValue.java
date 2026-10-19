package fjc.evaluate;

import java.util.LinkedHashMap;
import java.util.Map;

/** A runtime object with attributes (the Flask app, request, jinja_env, ...). */
public final class ObjectValue extends Value {
    private final String name;
    private final Map<String, Value> attributes = new LinkedHashMap<>();

    public ObjectValue(String name) { this.name = name; }

    public String name() { return name; }
    public Map<String, Value> attributes() { return attributes; }
    public Value get(String attr) { return attributes.get(attr); }
    public ObjectValue set(String attr, Value v) { attributes.put(attr, v); return this; }

    @Override public String typeName() { return name; }
    @Override public boolean isTruthy() { return true; }
    @Override public String asText() { return "<" + name + " object>"; }
    @Override public Object toJava() { return asText(); }
}
