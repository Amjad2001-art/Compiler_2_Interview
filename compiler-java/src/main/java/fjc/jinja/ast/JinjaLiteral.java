package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A constant inside a template expression: string, number, boolean or none. */
public final class JinjaLiteral extends JinjaExpression {
    public enum Kind { STRING, INTEGER, FLOAT, BOOLEAN, NONE }

    private final Kind kind;
    private final Object value;

    public JinjaLiteral(SourcePosition position, Kind kind, Object value) {
        super(position);
        this.kind = kind;
        this.value = value;
    }

    public Kind kind() { return kind; }
    public Object value() { return value; }
    public boolean isString() { return kind == Kind.STRING; }
    public String stringValue() { return (String) value; }

    @Override public String nodeName() { return "Literal"; }
    @Override public List<JinjaAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("kind", kind.name().toLowerCase());
        p.put("value", value);
        return p;
    }
    @Override public String label() { return "Literal: " + formatValue(value); }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitLiteral(this); }
}
