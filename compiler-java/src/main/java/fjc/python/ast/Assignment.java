package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/**
 * {@code target = value} (or augmented {@code target += value}).
 * The target is an Identifier, AttributeAccess or SubscriptAccess.
 */
public final class Assignment extends PythonStatement {
    private final PythonExpression target;
    private final PythonExpression value;
    private final String operator;

    public Assignment(SourcePosition position, PythonExpression target, PythonExpression value, String operator) {
        super(position);
        this.target = target;
        this.value = value;
        this.operator = operator;
    }

    public PythonExpression target() { return target; }
    public PythonExpression value() { return value; }
    public String operator() { return operator; }
    public boolean isAugmented() { return !"=".equals(operator); }

    /** The simple variable name when the target is an Identifier, else null. */
    public String targetName() {
        return target instanceof Identifier ? ((Identifier) target).name() : null;
    }

    @Override public String nodeName() { return "Assignment"; }
    @Override public List<PythonExpression> children() { return List.of(target, value); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        if (targetName() != null) p.put("target", targetName());
        if (isAugmented()) p.put("operator", operator);
        return p;
    }
    @Override public String label() {
        return targetName() != null ? "Assignment: " + targetName() + (isAugmented() ? " " + operator : "")
                : "Assignment" + (isAugmented() ? " " + operator : "");
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitAssignment(this); }
}
