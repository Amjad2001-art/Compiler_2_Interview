package fjc.css;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code @media (...) { rules }}, {@code @keyframes name { ... }}, {@code @import ...;} */
public final class CssAtRule extends CssAstNode {
    private final String name;
    private final String prelude;
    private final List<CssAstNode> rules;
    private final List<CssDeclaration> declarations;

    public CssAtRule(SourcePosition position, String name, String prelude, List<CssAstNode> rules, List<CssDeclaration> declarations) {
        super(position);
        this.name = name;
        this.prelude = prelude;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.declarations = Collections.unmodifiableList(new ArrayList<>(declarations));
    }

    public String name() { return name; }
    public String prelude() { return prelude; }
    public List<CssAstNode> rules() { return rules; }
    public List<CssDeclaration> declarations() { return declarations; }

    @Override public String nodeName() { return "CssAtRule"; }
    @Override public List<CssAstNode> children() {
        List<CssAstNode> out = new ArrayList<>(rules);
        out.addAll(declarations);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        p.put("prelude", prelude);
        return p;
    }
    @Override public String label() { return "CssAtRule: @" + name + " " + prelude; }
}
