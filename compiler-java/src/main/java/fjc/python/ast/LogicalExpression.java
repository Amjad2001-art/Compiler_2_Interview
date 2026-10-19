package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code left and right} / {@code left or right} */
public final class LogicalExpression extends PythonExpression {
    private final String operator;
    private final PythonExpression left;
    private final PythonExpression right;

    public LogicalExpression(SourcePosition position, String operator, PythonExpression left, PythonExpression right) {
        super(position);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String operator() { return operator; }
    public PythonExpression left() { return left; }
    public PythonExpression right() { return right; }

    @Override public String nodeName() { return "LogicalExpression"; }
    @Override public List<PythonExpression> children() { return List.of(left, right); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("operator", operator);
        return p;
    }
    @Override public String label() { return "LogicalExpression: " + operator; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitLogical(this); }
}
