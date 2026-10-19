package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;

/** {@code target[index]}, e.g. {@code product['name']} or {@code items[0]}. */
public final class IndexAccess extends JinjaExpression {
    private final JinjaExpression target;
    private final JinjaExpression index;

    public IndexAccess(SourcePosition position, JinjaExpression target, JinjaExpression index) {
        super(position);
        this.target = target;
        this.index = index;
    }

    public JinjaExpression target() { return target; }
    public JinjaExpression index() { return index; }

    @Override public String nodeName() { return "IndexAccess"; }
    @Override public List<JinjaExpression> children() { return List.of(target, index); }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitIndexAccess(this); }
}
