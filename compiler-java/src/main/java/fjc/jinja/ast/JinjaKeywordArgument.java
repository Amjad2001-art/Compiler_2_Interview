package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.List;
import java.util.Map;

/** {@code name=value} inside a call, filter or url_for(). */
public final class JinjaKeywordArgument extends JinjaAstNode {
    private final String name;
    private final JinjaExpression value;

    public JinjaKeywordArgument(SourcePosition position, String name, JinjaExpression value) {
        super(position);
        this.name = name;
        this.value = value;
    }

    public String name() { return name; }
    public JinjaExpression value() { return value; }

    @Override public String nodeName() { return "KeywordArgument"; }
    @Override public List<JinjaExpression> children() { return List.of(value); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "KeywordArgument: " + name + "="; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitKeywordArgument(this); }
}
