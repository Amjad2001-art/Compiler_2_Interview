package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;

/** {@code {{ expression }}} */
public final class OutputExpression extends JinjaAstNode {
    private final JinjaExpression expression;

    public OutputExpression(SourcePosition position, JinjaExpression expression) {
        super(position);
        this.expression = expression;
    }

    public JinjaExpression expression() { return expression; }

    @Override public String nodeName() { return "OutputExpression"; }
    @Override public List<JinjaExpression> children() { return List.of(expression); }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitOutput(this); }
}
