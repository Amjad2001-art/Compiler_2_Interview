package fjc.evaluate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Variable environment with lexical parent (module → function). */
public final class Environment {
    private final Environment parent;
    private final Map<String, Value> variables = new LinkedHashMap<>();

    public Environment(Environment parent) { this.parent = parent; }

    public Environment parent() { return parent; }
    public Map<String, Value> variables() { return variables; }

    public boolean has(String name) {
        for (Environment e = this; e != null; e = e.parent) if (e.variables.containsKey(name)) return true;
        return false;
    }

    public Value get(String name) {
        for (Environment e = this; e != null; e = e.parent) {
            Value v = e.variables.get(name);
            if (v != null) return v;
        }
        return null;
    }

    public void define(String name, Value value) { variables.put(name, value); }

    /** Assigns to the nearest environment that already holds the name, else defines locally. */
    public void assign(String name, Value value) {
        for (Environment e = this; e != null; e = e.parent) {
            if (e.variables.containsKey(name)) { e.variables.put(name, value); return; }
        }
        variables.put(name, value);
    }
}
