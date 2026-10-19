package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;

/** An expression evaluated for its side effects, e.g. {@code products.append(p)}. */
public final class ExpressionStatement extends PythonStatement {
    private final PythonExpression expression;

    public ExpressionStatement(SourcePosition position, PythonExpression expression) {
        super(position);
        this.expression = expression;
    }

    public PythonExpression expression() { return expression; }

    @Override public String nodeName() { return "ExpressionStatement"; }
    @Override public List<PythonExpression> children() { return List.of(expression); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitExpressionStatement(this); }
}
