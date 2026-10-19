package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Literal text (HTML text content or text inside an attribute value). */
public final class TextNode extends JinjaAstNode {
    private final String text;

    public TextNode(SourcePosition position, String text) {
        super(position);
        this.text = text;
    }

    public String text() { return text; }
    public boolean isWhitespace() { return text.trim().isEmpty(); }

    @Override public String nodeName() { return "TextNode"; }
    @Override public List<JinjaAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("text", text);
        return p;
    }
    @Override public String label() {
        String shown = text.trim().replace("\n", "\\n");
        if (shown.length() > 40) shown = shown.substring(0, 37) + "...";
        return "TextNode: \"" + shown + "\"";
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitText(this); }
}
