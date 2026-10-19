package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code not operand}, {@code -operand}, {@code +operand} */
public final class UnaryExpression extends PythonExpression {
    private final String operator;
    private final PythonExpression operand;

    public UnaryExpression(SourcePosition position, String operator, PythonExpression operand) {
        super(position);
        this.operator = operator;
        this.operand = operand;
    }

    public String operator() { return operator; }
    public PythonExpression operand() { return operand; }

    @Override public String nodeName() { return "UnaryExpression"; }
    @Override public List<PythonExpression> children() { return List.of(operand); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("operator", operator);
        return p;
    }
    @Override public String label() { return "UnaryExpression: " + operator; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitUnary(this); }
}
