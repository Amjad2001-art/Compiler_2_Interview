package fjc.jinja.ast;

import fjc.ast.AstNode;
import fjc.common.SourcePosition;

/** Base class of all Jinja/HTML AST nodes; adds the visitor entry point. */
public abstract class JinjaAstNode extends AstNode {
    protected JinjaAstNode(SourcePosition position) { super(position); }

    public abstract <R> R accept(JinjaAstVisitor<R> visitor);
}
