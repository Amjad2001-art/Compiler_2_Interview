package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code name="value parts"}; the value is a sequence of TextNode /
 * OutputExpression / IfStatement parts, or null for a boolean attribute.
 */
public final class HtmlAttribute extends JinjaAstNode {
    private final String name;
    private final List<JinjaAstNode> valueParts;
    private final boolean quoted;

    public HtmlAttribute(SourcePosition position, String name, List<JinjaAstNode> valueParts, boolean quoted) {
        super(position);
        this.name = name;
        this.valueParts = valueParts == null ? null : Collections.unmodifiableList(new ArrayList<>(valueParts));
        this.quoted = quoted;
    }

    public String name() { return name; }
    public boolean hasValue() { return valueParts != null; }
    public List<JinjaAstNode> valueParts() { return valueParts == null ? Collections.emptyList() : valueParts; }
    public boolean isQuoted() { return quoted; }

    /** The static text of the value when it contains no Jinja parts, else null. */
    public String staticValue() {
        if (valueParts == null) return null;
        StringBuilder sb = new StringBuilder();
        for (JinjaAstNode part : valueParts) {
            if (!(part instanceof TextNode)) return null;
            sb.append(((TextNode) part).text());
        }
        return sb.toString();
    }

    @Override public String nodeName() { return "HtmlAttribute"; }
    @Override public List<JinjaAstNode> children() { return valueParts(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        if (staticValue() != null) p.put("value", staticValue());
        return p;
    }
    @Override public String label() {
        if (valueParts == null) return "HtmlAttribute: " + name;
        String sv = staticValue();
        return "HtmlAttribute: " + name + (sv != null ? "=" + formatValue(sv) : "=<dynamic>");
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitAttribute(this); }
}
