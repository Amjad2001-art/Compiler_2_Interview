package fjc.css;

import fjc.parsetree.RuleNode;
import fjc.parsetree.TokenNode;

import java.util.ArrayList;
import java.util.List;

/** Builds the CSS AST (Stylesheet / CssRule / CssAtRule / CssDeclaration) from the parse tree. */
// بناء الشجرة المجردة لقواعد التنسيق من نتيجة المحلل النحوي لـ CSS.
public final class CssAstBuilder {
    private final String fileName;

    public CssAstBuilder(String fileName) { this.fileName = fileName; }

    // هذا الباني يحوّل شجرة قواعد التنسيق إلى عقد تمثل القواعد والتصريحات والقيم.
    public Stylesheet build(RuleNode stylesheet) {
        return new Stylesheet(stylesheet.position(), fileName, buildRules(stylesheet));
    }

    private List<CssAstNode> buildRules(RuleNode parent) {
        List<CssAstNode> out = new ArrayList<>();
        for (RuleNode r : parent.rules()) {
            if (r.ruleName().equals("qualified_rule")) out.add(buildQualified(r));
            else if (r.ruleName().equals("at_rule")) out.add(buildAt(r));
        }
        return out;
    }

    private CssRule buildQualified(RuleNode rule) {
        List<String> selectors = new ArrayList<>();
        for (RuleNode sel : rule.rule("selector_list").rules("selector")) selectors.add(joinSelector(sel));
        return new CssRule(rule.position(), selectors, buildDeclarations(rule.rule("declaration_list")));
    }

    private CssAtRule buildAt(RuleNode rule) {
        String name = rule.firstToken().text();
        String prelude = joinTokens(rule.rule("prelude"));
        List<CssAstNode> rules = new ArrayList<>();
        List<CssDeclaration> declarations = new ArrayList<>();
        RuleNode block = rule.rule("rule_block");
        if (block != null) rules = buildRules(block);
        RuleNode decls = rule.rule("declaration_list");
        if (decls != null) declarations = buildDeclarations(decls);
        return new CssAtRule(rule.position(), name, prelude, rules, declarations);
    }

    private List<CssDeclaration> buildDeclarations(RuleNode list) {
        List<CssDeclaration> out = new ArrayList<>();
        for (RuleNode d : list.rules("declaration")) {
            String property = d.firstToken().text();
            List<String> valueTokens = new ArrayList<>();
            for (TokenNode t : d.rule("value").tokens()) valueTokens.add(renderToken(t));
            String value = joinValue(d.rule("value"));
            out.add(new CssDeclaration(d.position(), property, value, d.rule("important") != null, valueTokens));
        }
        return out;
    }

    private static String renderToken(TokenNode t) {
        CssTokenType type = (CssTokenType) t.token().type();
        switch (type) {
            case HASH: return "#" + t.text();
            case STRING: return "\"" + t.text() + "\"";
            case FUNCTION: return t.text() + "(";
            case URL: return "url(" + t.text() + ")";
            default: return t.text();
        }
    }

    private static String joinValue(RuleNode value) {
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (TokenNode t : value.tokens()) {
            String text = renderToken(t);
            boolean noSpace = prev == null || prev.endsWith("(") || text.equals(")") || text.equals(",") || prev.equals("/") || text.equals("/");
            if (!noSpace) sb.append(' ');
            sb.append(text);
            prev = text;
        }
        return sb.toString();
    }

    private static String joinSelector(RuleNode selector) {
        StringBuilder sb = new StringBuilder();
        for (TokenNode t : selector.tokens()) {
            String text = renderToken(t);
            if (sb.length() > 0 && !(text.equals(")") || sb.charAt(sb.length() - 1) == '(' || text.equals(":") || text.equals(".")
                    || sb.charAt(sb.length() - 1) == ':' || sb.charAt(sb.length() - 1) == '.')) sb.append(' ');
            sb.append(text);
        }
        return sb.toString();
    }

    private static String joinTokens(RuleNode node) {
        StringBuilder sb = new StringBuilder();
        for (TokenNode t : node.tokens()) {
            if (sb.length() > 0 && !t.text().equals(")") && !t.text().equals(":")
                    && sb.charAt(sb.length() - 1) != '(' && sb.charAt(sb.length() - 1) != ':') sb.append(' ');
            sb.append(renderToken(t));
        }
        return sb.toString();
    }
}
