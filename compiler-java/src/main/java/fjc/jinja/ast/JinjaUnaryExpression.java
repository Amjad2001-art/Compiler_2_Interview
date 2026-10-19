package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code not operand}, {@code -operand}, {@code +operand} */
public final class JinjaUnaryExpression extends JinjaExpression {
    private final String operator;
    private final JinjaExpression operand;

    public JinjaUnaryExpression(SourcePosition position, String operator, JinjaExpression operand) {
        super(position);
        this.operator = operator;
        this.operand = operand;
    }

    public String operator() { return operator; }
    public JinjaExpression operand() { return operand; }

    @Override public String nodeName() { return "UnaryExpression"; }
    @Override public List<JinjaExpression> children() { return List.of(operand); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("operator", operator);
        return p;
    }
    @Override public String label() { return "UnaryExpression: " + operator; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitUnary(this); }
}
