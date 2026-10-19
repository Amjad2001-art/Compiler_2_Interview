package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code target.property}, e.g. {@code product.name} or {@code loop.index}. */
public final class PropertyAccess extends JinjaExpression {
    private final JinjaExpression target;
    private final String property;

    public PropertyAccess(SourcePosition position, JinjaExpression target, String property) {
        super(position);
        this.target = target;
        this.property = property;
    }

    public JinjaExpression target() { return target; }
    public String property() { return property; }

    /** Dotted text such as {@code product.name} when the chain starts at a variable, else null. */
    public String dottedName() {
        if (target instanceof VariableReference) return ((VariableReference) target).name() + "." + property;
        if (target instanceof PropertyAccess) {
            String inner = ((PropertyAccess) target).dottedName();
            return inner == null ? null : inner + "." + property;
        }
        return null;
    }

    @Override public String nodeName() { return "PropertyAccess"; }
    @Override public List<JinjaExpression> children() { return List.of(target); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("property", property);
        return p;
    }
    @Override public String label() {
        String d = dottedName();
        return "PropertyAccess: " + (d != null ? d : "." + property);
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitPropertyAccess(this); }
}
