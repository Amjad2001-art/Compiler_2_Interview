package fjc.python.ast;

import fjc.ast.AstNode;
import fjc.common.SourcePosition;

/** Base class of all Python AST nodes; adds the visitor entry point. */
public abstract class PythonAstNode extends AstNode {
    protected PythonAstNode(SourcePosition position) { super(position); }

    public abstract <R> R accept(PythonAstVisitor<R> visitor);
}
