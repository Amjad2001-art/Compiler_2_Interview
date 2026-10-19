package fjc.css;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Root of the CSS AST: the rules of one stylesheet file. */
public final class Stylesheet extends CssAstNode {
    private final String fileName;
    private final List<CssAstNode> rules;

    public Stylesheet(SourcePosition position, String fileName, List<CssAstNode> rules) {
        super(position);
        this.fileName = fileName;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public String fileNameOfSheet() { return fileName; }
    public List<CssAstNode> rules() { return rules; }

    /** Every declaration in the sheet, including those nested in at-rules. */
    public List<CssDeclaration> allDeclarations() {
        List<CssDeclaration> out = new ArrayList<>();
        collect(rules, out);
        return out;
    }

    private static void collect(List<? extends CssAstNode> nodes, List<CssDeclaration> out) {
        for (CssAstNode n : nodes) {
            if (n instanceof CssRule) out.addAll(((CssRule) n).declarations());
            else if (n instanceof CssAtRule) { collect(((CssAtRule) n).rules(), out); out.addAll(((CssAtRule) n).declarations()); }
        }
    }

    @Override public String nodeName() { return "Stylesheet"; }
    @Override public List<CssAstNode> children() { return rules; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("file", fileName);
        p.put("ruleCount", rules.size());
        return p;
    }
    @Override public String label() { return "Stylesheet: " + fileName + " (" + rules.size() + " rules)"; }
}
