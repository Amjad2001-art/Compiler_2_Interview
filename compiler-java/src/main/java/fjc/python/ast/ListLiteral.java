package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code [e1, e2, ...]} */
public final class ListLiteral extends PythonExpression {
    private final List<PythonExpression> elements;

    public ListLiteral(SourcePosition position, List<PythonExpression> elements) {
        super(position);
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    public List<PythonExpression> elements() { return elements; }

    @Override public String nodeName() { return "ListLiteral"; }
    @Override public List<PythonExpression> children() { return elements; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("size", elements.size());
        return p;
    }
    @Override public String label() { return "ListLiteral (" + elements.size() + " elements)"; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitListLiteral(this); }
}
