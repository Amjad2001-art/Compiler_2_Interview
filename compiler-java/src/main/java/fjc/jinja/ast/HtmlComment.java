package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code <!-- ... -->} */
public final class HtmlComment extends JinjaAstNode {
    private final String text;

    public HtmlComment(SourcePosition position, String text) {
        super(position);
        this.text = text;
    }

    public String text() { return text; }

    @Override public String nodeName() { return "HtmlComment"; }
    @Override public List<JinjaAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("text", text.trim());
        return p;
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitComment(this); }
}
