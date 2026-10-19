package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code target.attribute} */
public final class AttributeAccess extends PythonExpression {
    private final PythonExpression target;
    private final String attribute;

    public AttributeAccess(SourcePosition position, PythonExpression target, String attribute) {
        super(position);
        this.target = target;
        this.attribute = attribute;
    }

    public PythonExpression target() { return target; }
    public String attribute() { return attribute; }

    /** Dotted text such as {@code app.route} when the chain is made of identifiers, else null. */
    public String dottedName() {
        if (target instanceof Identifier) return ((Identifier) target).name() + "." + attribute;
        if (target instanceof AttributeAccess) {
            String inner = ((AttributeAccess) target).dottedName();
            return inner == null ? null : inner + "." + attribute;
        }
        return null;
    }

    @Override public String nodeName() { return "AttributeAccess"; }
    @Override public List<PythonExpression> children() { return List.of(target); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("attribute", attribute);
        return p;
    }
    @Override public String label() { return "AttributeAccess: ." + attribute; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitAttributeAccess(this); }
}
