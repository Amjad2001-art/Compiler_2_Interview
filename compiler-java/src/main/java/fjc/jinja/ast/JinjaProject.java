package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Root of the Jinja AST: every template of the project under one node. */
public final class JinjaProject extends JinjaAstNode {
    private final String directory;
    private final List<JinjaTemplate> templates;

    public JinjaProject(SourcePosition position, String directory, List<JinjaTemplate> templates) {
        super(position);
        this.directory = directory;
        this.templates = Collections.unmodifiableList(new ArrayList<>(templates));
    }

    public String directory() { return directory; }
    public List<JinjaTemplate> templates() { return templates; }

    public JinjaTemplate template(String name) {
        for (JinjaTemplate t : templates) if (t.name().equals(name)) return t;
        return null;
    }

    @Override public String nodeName() { return "JinjaProject"; }
    @Override public List<JinjaTemplate> children() { return templates; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("directory", directory);
        p.put("templateCount", templates.size());
        return p;
    }
    @Override public String label() { return "JinjaProject: " + directory + " (" + templates.size() + " templates)"; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitProject(this); }
}
