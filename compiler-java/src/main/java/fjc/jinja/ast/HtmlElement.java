package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An HTML element. Attributes may be plain {@link HtmlAttribute} nodes, Jinja
 * {@link OutputExpression}s or attribute-level {@link IfStatement}s
 * (e.g. {@code <option {% if selected %}selected{% endif %}>}).
 */
public final class HtmlElement extends JinjaAstNode {
    private final String tagName;
    private final List<JinjaAstNode> attributes;
    private final List<JinjaAstNode> children;
    private final boolean voidElement;

    public HtmlElement(SourcePosition position, String tagName, List<JinjaAstNode> attributes,
                       List<JinjaAstNode> children, boolean voidElement) {
        super(position);
        this.tagName = tagName;
        this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
        this.voidElement = voidElement;
    }

    public String tagName() { return tagName; }
    public List<JinjaAstNode> attributes() { return attributes; }
    public List<JinjaAstNode> body() { return children; }
    public boolean isVoid() { return voidElement; }

    /** Plain attribute by name (case-insensitive), or null. */
    public HtmlAttribute attribute(String name) {
        for (JinjaAstNode a : attributes) {
            if (a instanceof HtmlAttribute && ((HtmlAttribute) a).name().equalsIgnoreCase(name)) return (HtmlAttribute) a;
        }
        return null;
    }

    public List<HtmlAttribute> plainAttributes() {
        List<HtmlAttribute> out = new ArrayList<>();
        for (JinjaAstNode a : attributes) if (a instanceof HtmlAttribute) out.add((HtmlAttribute) a);
        return out;
    }

    @Override public String nodeName() { return "HtmlElement"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>(attributes);
        out.addAll(children);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("tag", tagName);
        if (voidElement) p.put("void", true);
        return p;
    }
    @Override public String label() { return "HtmlElement: <" + tagName + ">"; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitElement(this); }
}
