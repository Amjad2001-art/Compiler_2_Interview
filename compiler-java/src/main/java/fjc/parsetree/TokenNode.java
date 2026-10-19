package fjc.parsetree;

import fjc.common.SourcePosition;
import fjc.common.Token;

import java.util.Collections;
import java.util.List;

/** A parse-tree leaf wrapping one token (keyword, punctuation, name, literal...). */
public final class TokenNode extends ParseTreeNode {
    private final Token token;

    public TokenNode(Token token) { this.token = token; }

    public Token token() { return token; }
    public String text() { return token.text(); }

    @Override public String name() { return token.type().name(); }
    @Override public SourcePosition position() { return token.position(); }
    @Override public List<ParseTreeNode> children() { return Collections.emptyList(); }
    @Override public boolean isToken() { return true; }

    @Override public String toString() { return name() + " '" + text().replace("\n", "\\n") + "'"; }
}
