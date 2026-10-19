package fjc.css;

import fjc.ast.AstNode;
import fjc.common.SourcePosition;

/** Base class of CSS AST nodes (Stylesheet, CssRule, CssAtRule, CssDeclaration). */
public abstract class CssAstNode extends AstNode {
    protected CssAstNode(SourcePosition position) { super(position); }
}
