package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One template file (e.g. index.jinja) with its top-level nodes. */
public final class JinjaTemplate extends JinjaAstNode {
    private final String name;
    private final List<JinjaAstNode> children;

    public JinjaTemplate(SourcePosition position, String name, List<JinjaAstNode> children) {
        super(position);
        this.name = name;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    public String name() { return name; }

    @Override public String nodeName() { return "JinjaTemplate"; }
    @Override public List<JinjaAstNode> children() { return children; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "JinjaTemplate: " + name; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitTemplate(this); }
}
