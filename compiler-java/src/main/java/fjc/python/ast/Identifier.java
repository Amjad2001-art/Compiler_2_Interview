package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A variable / function name reference. */
public final class Identifier extends PythonExpression {
    private final String name;

    public Identifier(SourcePosition position, String name) {
        super(position);
        this.name = name;
    }

    public String name() { return name; }

    @Override public String nodeName() { return "Identifier"; }
    @Override public List<PythonAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "Identifier: " + name; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitIdentifier(this); }
}
