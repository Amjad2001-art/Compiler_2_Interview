package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A constant: string, integer, float, boolean or None.
 * The value is stored already decoded (Long, Double, String, Boolean or null).
 */
public final class Literal extends PythonExpression {
    public enum Kind { STRING, INTEGER, FLOAT, BOOLEAN, NONE }

    private final Kind kind;
    private final Object value;

    public Literal(SourcePosition position, Kind kind, Object value) {
        super(position);
        this.kind = kind;
        this.value = value;
    }

    public Kind kind() { return kind; }
    public Object value() { return value; }
    public boolean isString() { return kind == Kind.STRING; }
    public boolean isNumber() { return kind == Kind.INTEGER || kind == Kind.FLOAT; }
    public String stringValue() { return (String) value; }

    @Override public String nodeName() { return "Literal"; }
    @Override public List<PythonAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("kind", kind.name().toLowerCase());
        p.put("value", value);
        return p;
    }
    @Override public String label() { return "Literal: " + formatValue(value); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitLiteral(this); }
}
