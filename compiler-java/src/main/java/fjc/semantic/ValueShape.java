package fjc.semantic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Statically inferred "shape" (type) of a Python value or Jinja context
 * variable: a string, a number, a list of X, a dictionary with known keys, ...
 *
 * <p>Shapes are what the cross-language analysis works with: the Python side
 * infers that {@code products} is a list of dictionaries with keys
 * {id, name, price, ...}; the Jinja side then knows that {@code product.name}
 * is valid and {@code product.unknownField} is not.
 */
public final class ValueShape {
    public enum Kind { ANY, NONE, BOOLEAN, INTEGER, FLOAT, STRING, LIST, DICT, OBJECT, FUNCTION }

    public static final ValueShape ANY = new ValueShape(Kind.ANY, null, null, null, false);
    public static final ValueShape NONE = new ValueShape(Kind.NONE, null, null, null, false);
    public static final ValueShape BOOLEAN = new ValueShape(Kind.BOOLEAN, null, null, null, false);
    public static final ValueShape INTEGER = new ValueShape(Kind.INTEGER, null, null, null, false);
    public static final ValueShape FLOAT = new ValueShape(Kind.FLOAT, null, null, null, false);
    public static final ValueShape STRING = new ValueShape(Kind.STRING, null, null, null, false);

    private final Kind kind;
    private final ValueShape element;                 // LIST
    private final Map<String, ValueShape> fields;      // DICT / OBJECT (null = unknown/open fields)
    private final String objectName;                   // OBJECT / FUNCTION
    private final boolean optional;                    // may also be None

    private ValueShape(Kind kind, ValueShape element, Map<String, ValueShape> fields, String objectName, boolean optional) {
        this.kind = kind;
        this.element = element;
        this.fields = fields == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.objectName = objectName;
        this.optional = optional;
    }

    public static ValueShape list(ValueShape element) {
        return new ValueShape(Kind.LIST, element == null ? ANY : element, null, null, false);
    }

    public static ValueShape dict(Map<String, ValueShape> fields) {
        return new ValueShape(Kind.DICT, null, fields, null, false);
    }

    /** A dictionary whose keys are not statically known. */
    public static ValueShape openDict() {
        return new ValueShape(Kind.DICT, null, null, null, false);
    }

    public static ValueShape object(String name, Map<String, ValueShape> fields) {
        return new ValueShape(Kind.OBJECT, null, fields, name, false);
    }

    public static ValueShape function(String name) {
        return new ValueShape(Kind.FUNCTION, null, null, name, false);
    }

    public Kind kind() { return kind; }
    public ValueShape element() { return element == null ? ANY : element; }
    /** Known fields, or null when the fields are not statically known (open). */
    public Map<String, ValueShape> fields() { return fields; }
    public String objectName() { return objectName; }
    public boolean isOptional() { return optional; }

    public boolean isAny() { return kind == Kind.ANY; }
    public boolean isNumber() { return kind == Kind.INTEGER || kind == Kind.FLOAT; }
    public boolean isString() { return kind == Kind.STRING; }
    public boolean isBoolean() { return kind == Kind.BOOLEAN; }
    public boolean isNone() { return kind == Kind.NONE; }
    public boolean isList() { return kind == Kind.LIST; }
    public boolean isDict() { return kind == Kind.DICT; }
    public boolean isObject() { return kind == Kind.OBJECT; }
    public boolean isFunction() { return kind == Kind.FUNCTION; }
    /** Something a template can loop over. */
    public boolean isIterable() { return kind == Kind.LIST || kind == Kind.DICT || kind == Kind.ANY; }
    public boolean hasKnownFields() { return (kind == Kind.DICT || kind == Kind.OBJECT) && fields != null; }
    public boolean hasField(String name) { return hasKnownFields() && fields.containsKey(name); }

    /** Shape of a field/property; ANY when unknown. */
    public ValueShape field(String name) {
        if (fields != null && fields.containsKey(name)) return fields.get(name);
        return ANY;
    }

    public ValueShape asOptional() {
        if (optional || kind == Kind.NONE || kind == Kind.ANY) return this;
        return new ValueShape(kind, element, fields, objectName, true);
    }

    /** Merge two shapes (used for return statements and list elements). */
    public ValueShape union(ValueShape other) {
        if (other == null) return this;
        if (this == other) return this;
        if (kind == Kind.NONE) return other.asOptional();
        if (other.kind == Kind.NONE) return this.asOptional();
        if (kind == Kind.ANY || other.kind == Kind.ANY) return ANY;
        if (kind != other.kind) {
            if (isNumber() && other.isNumber()) return FLOAT;
            return ANY;
        }
        boolean opt = optional || other.optional;
        switch (kind) {
            case LIST: {
                ValueShape merged = list(element().union(other.element()));
                return opt ? merged.asOptional() : merged;
            }
            case DICT:
            case OBJECT: {
                if (fields == null || other.fields == null) {
                    ValueShape open = kind == Kind.DICT ? openDict() : object(objectName, null);
                    return opt ? open.asOptional() : open;
                }
                Map<String, ValueShape> merged = new LinkedHashMap<>(fields);
                for (Map.Entry<String, ValueShape> e : other.fields.entrySet()) {
                    merged.merge(e.getKey(), e.getValue(), ValueShape::union);
                }
                ValueShape result = kind == Kind.DICT ? dict(merged) : object(objectName, merged);
                return opt ? result.asOptional() : result;
            }
            default:
                return opt ? this.asOptional() : this;
        }
    }

    public String describe() {
        String base;
        switch (kind) {
            case ANY: base = "any"; break;
            case NONE: base = "None"; break;
            case BOOLEAN: base = "bool"; break;
            case INTEGER: base = "int"; break;
            case FLOAT: base = "float"; break;
            case STRING: base = "str"; break;
            case LIST: base = "list[" + element().describe() + "]"; break;
            case DICT: base = fields == null ? "dict" : "dict" + fields.keySet(); break;
            case OBJECT: base = "object<" + objectName + ">"; break;
            case FUNCTION: base = "function<" + objectName + ">"; break;
            default: base = kind.name().toLowerCase();
        }
        return optional ? base + " | None" : base;
    }

    @Override public String toString() { return describe(); }
}
