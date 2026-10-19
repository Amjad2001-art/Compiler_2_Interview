package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;

/** {@code pass} */
public final class PassStatement extends PythonStatement {
    public PassStatement(SourcePosition position) { super(position); }

    @Override public String nodeName() { return "PassStatement"; }
    @Override public List<PythonAstNode> children() { return Collections.emptyList(); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitPass(this); }
}
