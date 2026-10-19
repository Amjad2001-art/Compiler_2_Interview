package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A function parameter with an optional default value. */
public final class Parameter extends PythonAstNode {
    private final String name;
    private final PythonExpression defaultValue;

    public Parameter(SourcePosition position, String name, PythonExpression defaultValue) {
        super(position);
        this.name = name;
        this.defaultValue = defaultValue;
    }

    public String name() { return name; }
    public PythonExpression defaultValue() { return defaultValue; }

    @Override public String nodeName() { return "Parameter"; }
    @Override public List<PythonExpression> children() {
        return defaultValue == null ? Collections.emptyList() : List.of(defaultValue);
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitParameter(this); }
}
