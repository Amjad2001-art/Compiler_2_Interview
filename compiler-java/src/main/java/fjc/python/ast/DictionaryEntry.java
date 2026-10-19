package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** One {@code key: value} pair of a dictionary literal. */
public final class DictionaryEntry extends PythonAstNode {
    private final PythonExpression key;
    private final PythonExpression value;

    public DictionaryEntry(SourcePosition position, PythonExpression key, PythonExpression value) {
        super(position);
        this.key = key;
        this.value = value;
    }

    public PythonExpression key() { return key; }
    public PythonExpression value() { return value; }

    /** The key as a string when it is a string literal, otherwise null. */
    public String keyName() {
        if (key instanceof Literal && ((Literal) key).isString()) return ((Literal) key).stringValue();
        return null;
    }

    @Override public String nodeName() { return "DictionaryEntry"; }
    @Override public List<PythonExpression> children() {
        return keyName() != null ? List.of(value) : List.of(key, value);
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        if (keyName() != null) p.put("key", keyName());
        return p;
    }
    @Override public String label() {
        String k = keyName() != null ? keyName() : "<expr>";
        if (value instanceof Literal) return k + " = " + formatValue(((Literal) value).value());
        return "DictionaryEntry: " + k;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitDictionaryEntry(this); }
}
