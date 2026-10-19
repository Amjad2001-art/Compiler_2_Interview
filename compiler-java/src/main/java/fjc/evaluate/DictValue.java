package fjc.evaluate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Python dict with string keys (mutable, insertion ordered). */
public final class DictValue extends Value {
    private final Map<String, Value> entries;

    public DictValue(Map<String, Value> entries) { this.entries = new LinkedHashMap<>(entries); }

    public DictValue() { this.entries = new LinkedHashMap<>(); }

    public Map<String, Value> entries() { return entries; }
    public Value get(String key) { return entries.get(key); }
    public boolean has(String key) { return entries.containsKey(key); }
    public void put(String key, Value value) { entries.put(key, value); }

    @Override public String typeName() { return "dict"; }
    @Override public boolean isTruthy() { return !entries.isEmpty(); }
    @Override public String asText() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Value> e : entries.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append('\'').append(e.getKey()).append("': ").append(e.getValue().repr());
        }
        return sb.append('}').toString();
    }
    @Override public Object toJava() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Value> e : entries.entrySet()) out.put(e.getKey(), e.getValue().toJava());
        return out;
    }
}
