package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code name=value} inside a call; for render_template these are the context variables. */
public final class KeywordArgument extends PythonAstNode {
    private final String name;
    private final PythonExpression value;

    public KeywordArgument(SourcePosition position, String name, PythonExpression value) {
        super(position);
        this.name = name;
        this.value = value;
    }

    public String name() { return name; }
    public PythonExpression value() { return value; }

    @Override public String nodeName() { return "KeywordArgument"; }
    @Override public List<PythonExpression> children() { return List.of(value); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "KeywordArgument: " + name + "="; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitKeywordArgument(this); }
}
