package fjc.css;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code selector, selector { declarations }} */
public final class CssRule extends CssAstNode {
    private final List<String> selectors;
    private final List<CssDeclaration> declarations;

    public CssRule(SourcePosition position, List<String> selectors, List<CssDeclaration> declarations) {
        super(position);
        this.selectors = Collections.unmodifiableList(new ArrayList<>(selectors));
        this.declarations = Collections.unmodifiableList(new ArrayList<>(declarations));
    }

    public List<String> selectors() { return selectors; }
    public List<CssDeclaration> declarations() { return declarations; }
    public String selectorText() { return String.join(", ", selectors); }

    @Override public String nodeName() { return "CssRule"; }
    @Override public List<CssDeclaration> children() { return declarations; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("selectors", selectors);
        return p;
    }
    @Override public String label() { return "CssRule: " + selectorText(); }
}
