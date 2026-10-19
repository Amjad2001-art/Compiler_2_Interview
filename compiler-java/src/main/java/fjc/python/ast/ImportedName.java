package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One imported name with an optional alias: {@code name as alias}. */
public final class ImportedName extends PythonAstNode {
    private final String name;
    private final String alias;

    public ImportedName(SourcePosition position, String name, String alias) {
        super(position);
        this.name = name;
        this.alias = alias;
    }

    public String name() { return name; }
    public String alias() { return alias; }
    /** The identifier that becomes visible in the module scope. */
    public String boundName() { return alias != null ? alias : name; }

    @Override public String nodeName() { return "ImportedName"; }
    @Override public List<PythonAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        if (alias != null) p.put("alias", alias);
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitImportedName(this); }
}
