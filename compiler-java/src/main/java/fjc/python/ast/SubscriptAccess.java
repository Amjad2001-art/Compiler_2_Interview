package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code target[index]} */
public final class SubscriptAccess extends PythonExpression {
    private final PythonExpression target;
    private final PythonExpression index;

    public SubscriptAccess(SourcePosition position, PythonExpression target, PythonExpression index) {
        super(position);
        this.target = target;
        this.index = index;
    }

    public PythonExpression target() { return target; }
    public PythonExpression index() { return index; }

    /** The key when the index is a string literal, else null. */
    public String keyName() {
        if (index instanceof Literal && ((Literal) index).isString()) return ((Literal) index).stringValue();
        return null;
    }

    @Override public String nodeName() { return "SubscriptAccess"; }
    @Override public List<PythonExpression> children() { return List.of(target, index); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        if (keyName() != null) p.put("key", keyName());
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitSubscriptAccess(this); }
}
