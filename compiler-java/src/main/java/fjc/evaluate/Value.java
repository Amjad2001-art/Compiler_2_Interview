package fjc.evaluate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime value of the compile-time evaluator. The evaluator executes the
 * validated Python AST (module-level data + GET route bodies) to obtain the
 * concrete context data that the generator binds to the Jinja templates.
 */
public abstract class Value {
    public abstract String typeName();
    public abstract boolean isTruthy();
    /** Python {@code str()} of the value. */
    public abstract String asText();
    /** Plain Java representation (for JSON/log output). */
    public abstract Object toJava();

    public boolean isNone() { return false; }

    /** Python {@code repr()}: strings are quoted. */
    public String repr() { return asText(); }

    public static Value of(Object o) {
        if (o == null) return NoneValue.NONE;
        if (o instanceof Value) return (Value) o;
        if (o instanceof Boolean) return BooleanValue.of(((Boolean) o).booleanValue());
        if (o instanceof Long || o instanceof Integer) return NumberValue.ofLong(((Number) o).longValue());
        if (o instanceof Number) return NumberValue.ofDouble(((Number) o).doubleValue());
        if (o instanceof String) return new StringValue((String) o);
        if (o instanceof List) {
            List<Value> items = new ArrayList<>();
            for (Object x : (List<?>) o) items.add(of(x));
            return new ListValue(items);
        }
        if (o instanceof Map) {
            Map<String, Value> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) m.put(String.valueOf(e.getKey()), of(e.getValue()));
            return new DictValue(m);
        }
        throw new IllegalArgumentException("Cannot convert " + o.getClass());
    }

    /** Python equality (==). */
    public boolean equalsValue(Value other) {
        if (this instanceof NumberValue && other instanceof NumberValue) {
            return ((NumberValue) this).doubleValue() == ((NumberValue) other).doubleValue();
        }
        if (this instanceof BooleanValue && other instanceof NumberValue) {
            return (isTruthy() ? 1 : 0) == ((NumberValue) other).doubleValue();
        }
        if (this instanceof NumberValue && other instanceof BooleanValue) {
            return ((NumberValue) this).doubleValue() == (other.isTruthy() ? 1 : 0);
        }
        if (this instanceof ListValue && other instanceof ListValue) {
            List<Value> a = ((ListValue) this).items();
            List<Value> b = ((ListValue) other).items();
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!a.get(i).equalsValue(b.get(i))) return false;
            return true;
        }
        if (this instanceof DictValue && other instanceof DictValue) {
            Map<String, Value> a = ((DictValue) this).entries();
            Map<String, Value> b = ((DictValue) other).entries();
            if (!a.keySet().equals(b.keySet())) return false;
            for (String k : a.keySet()) if (!a.get(k).equalsValue(b.get(k))) return false;
            return true;
        }
        if (this.getClass() != other.getClass()) return false;
        return toJava() == null ? other.toJava() == null : toJava().equals(other.toJava());
    }

    @Override public String toString() { return repr(); }
}
