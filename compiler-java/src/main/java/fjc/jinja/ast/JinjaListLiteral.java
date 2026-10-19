package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code [a, b, c]} inside a template expression. */
public final class JinjaListLiteral extends JinjaExpression {
    private final List<JinjaExpression> elements;

    public JinjaListLiteral(SourcePosition position, List<JinjaExpression> elements) {
        super(position);
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    public List<JinjaExpression> elements() { return elements; }

    @Override public String nodeName() { return "ListLiteral"; }
    @Override public List<JinjaExpression> children() { return elements; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("size", elements.size());
        return p;
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitListLiteral(this); }
}
