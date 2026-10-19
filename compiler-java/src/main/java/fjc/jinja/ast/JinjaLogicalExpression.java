package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code left and right} / {@code left or right} */
public final class JinjaLogicalExpression extends JinjaExpression {
    private final String operator;
    private final JinjaExpression left;
    private final JinjaExpression right;

    public JinjaLogicalExpression(SourcePosition position, String operator, JinjaExpression left, JinjaExpression right) {
        super(position);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String operator() { return operator; }
    public JinjaExpression left() { return left; }
    public JinjaExpression right() { return right; }

    @Override public String nodeName() { return "LogicalExpression"; }
    @Override public List<JinjaExpression> children() { return List.of(left, right); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("operator", operator);
        return p;
    }
    @Override public String label() { return "LogicalExpression: " + operator; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitLogical(this); }
}
