package fjc.evaluate;

import java.util.ArrayList;
import java.util.List;

/** Python list (mutable). */
public final class ListValue extends Value {
    private final List<Value> items;

    public ListValue(List<Value> items) { this.items = new ArrayList<>(items); }

    public List<Value> items() { return items; }
    public int size() { return items.size(); }

    @Override public String typeName() { return "list"; }
    @Override public boolean isTruthy() { return !items.isEmpty(); }
    @Override public String asText() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i).repr());
        }
        return sb.append(']').toString();
    }
    @Override public Object toJava() {
        List<Object> out = new ArrayList<>();
        for (Value v : items) out.add(v.toJava());
        return out;
    }
}
