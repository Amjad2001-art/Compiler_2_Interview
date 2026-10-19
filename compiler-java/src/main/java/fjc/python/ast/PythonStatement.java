package fjc.python.ast;

import fjc.common.SourcePosition;

/** Marker base class for Python statement nodes. */
public abstract class PythonStatement extends PythonAstNode {
    protected PythonStatement(SourcePosition position) { super(position); }
}
