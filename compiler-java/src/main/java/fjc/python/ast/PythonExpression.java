package fjc.python.ast;

import fjc.common.SourcePosition;

/** Marker base class for Python expression nodes. */
public abstract class PythonExpression extends PythonAstNode {
    protected PythonExpression(SourcePosition position) { super(position); }
}
