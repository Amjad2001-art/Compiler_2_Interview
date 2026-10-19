package fjc.css;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code property: value [!important]} */
public final class CssDeclaration extends CssAstNode {
    private final String property;
    private final String value;
    private final boolean important;
    private final List<String> valueTokens;

    public CssDeclaration(SourcePosition position, String property, String value, boolean important, List<String> valueTokens) {
        super(position);
        this.property = property;
        this.value = value;
        this.important = important;
        this.valueTokens = Collections.unmodifiableList(valueTokens);
    }

    public String property() { return property; }
    public String value() { return value; }
    public boolean isImportant() { return important; }
    public List<String> valueTokens() { return valueTokens; }
    public boolean isCustomProperty() { return property.startsWith("--"); }

    @Override public String nodeName() { return "CssDeclaration"; }
    @Override public List<CssAstNode> children() { return Collections.emptyList(); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("property", property);
        p.put("value", value);
        if (important) p.put("important", true);
        return p;
    }
    @Override public String label() { return "CssDeclaration: " + property + ": " + value + (important ? " !important" : ""); }
}
