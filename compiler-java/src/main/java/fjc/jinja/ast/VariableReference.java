package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A bare variable name such as {@code products} or {@code product}. */
public final class VariableReference extends JinjaExpression {
    private final String name;

    public VariableReference(SourcePosition position, String name) {
        super(position);
        this.name = name;
    }

    public String name() { return name; }

    @Override public String nodeName() { return "VariableReference"; }
    @Override public List<JinjaAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "VariableReference: " + name; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitVariable(this); }
}
