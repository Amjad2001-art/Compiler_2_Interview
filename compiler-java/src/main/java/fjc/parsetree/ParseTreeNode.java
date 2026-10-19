package fjc.parsetree;

import fjc.common.SourcePosition;

import java.util.List;

/**
 * Base class of the concrete syntax tree (parse tree). The parse tree keeps
 * every grammar production and every token (punctuation, keywords and
 * delimiters). It is deliberately verbose; the AST builders compress it into
 * a much smaller semantic tree.
 */
public abstract class ParseTreeNode {
    public abstract String name();
    public abstract SourcePosition position();
    public abstract List<ParseTreeNode> children();
    public abstract boolean isToken();

    /** Total number of nodes in this subtree (including this node). */
    public int nodeCount() {
        int n = 1;
        for (ParseTreeNode c : children()) n += c.nodeCount();
        return n;
    }

    /** Number of token leaves in this subtree. */
    public int tokenCount() {
        if (isToken()) return 1;
        int n = 0;
        for (ParseTreeNode c : children()) n += c.tokenCount();
        return n;
    }

    public int depth() {
        int d = 0;
        for (ParseTreeNode c : children()) d = Math.max(d, c.depth());
        return d + 1;
    }
}
