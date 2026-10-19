package fjc.parsetree;

import fjc.common.SourcePosition;
import fjc.common.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A parse-tree interior node: one grammar production and its matched parts. */
public final class RuleNode extends ParseTreeNode {
    private final String ruleName;
    private final SourcePosition position;
    private final List<ParseTreeNode> children = new ArrayList<>();

    public RuleNode(String ruleName, SourcePosition position) {
        this.ruleName = ruleName;
        this.position = position;
    }

    public RuleNode add(ParseTreeNode child) {
        if (child != null) children.add(child);
        return this;
    }

    public RuleNode addToken(Token token) {
        children.add(new TokenNode(token));
        return this;
    }

    public String ruleName() { return ruleName; }

    @Override public String name() { return ruleName; }
    @Override public SourcePosition position() { return position; }
    @Override public List<ParseTreeNode> children() { return Collections.unmodifiableList(children); }
    @Override public boolean isToken() { return false; }

    public ParseTreeNode child(int i) { return children.get(i); }
    public int size() { return children.size(); }

    /** First child rule with the given name, or null. */
    public RuleNode rule(String name) {
        for (ParseTreeNode c : children) if (!c.isToken() && c.name().equals(name)) return (RuleNode) c;
        return null;
    }

    public List<RuleNode> rules(String name) {
        List<RuleNode> out = new ArrayList<>();
        for (ParseTreeNode c : children) if (!c.isToken() && c.name().equals(name)) out.add((RuleNode) c);
        return out;
    }

    public List<RuleNode> rules() {
        List<RuleNode> out = new ArrayList<>();
        for (ParseTreeNode c : children) if (!c.isToken()) out.add((RuleNode) c);
        return out;
    }

    public List<TokenNode> tokens() {
        List<TokenNode> out = new ArrayList<>();
        for (ParseTreeNode c : children) if (c.isToken()) out.add((TokenNode) c);
        return out;
    }

    public boolean hasToken(String text) {
        for (ParseTreeNode c : children) if (c.isToken() && ((TokenNode) c).text().equals(text)) return true;
        return false;
    }

    public TokenNode firstToken() {
        for (ParseTreeNode c : children) if (c.isToken()) return (TokenNode) c;
        return null;
    }

    /** Collects every token text in this subtree, in source order. */
    public List<String> tokenTexts() {
        List<String> out = new ArrayList<>();
        collectTokens(this, out);
        return out;
    }

    private static void collectTokens(ParseTreeNode n, List<String> out) {
        if (n.isToken()) { out.add(((TokenNode) n).text()); return; }
        for (ParseTreeNode c : n.children()) collectTokens(c, out);
    }

    @Override public String toString() { return ruleName; }
}
