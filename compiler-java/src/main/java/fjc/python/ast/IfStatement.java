package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code if c1: ... elif c2: ... else: ...}. The if/elif branches are
 * {@link ConditionalBranch} nodes; the else body is stored separately.
 */
public final class IfStatement extends PythonStatement {
    private final List<ConditionalBranch> branches;
    private final List<PythonStatement> elseBody;

    public IfStatement(SourcePosition position, List<ConditionalBranch> branches, List<PythonStatement> elseBody) {
        super(position);
        this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
        this.elseBody = elseBody == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(elseBody));
    }

    public List<ConditionalBranch> branches() { return branches; }
    public List<PythonStatement> elseBody() { return elseBody; }
    public boolean hasElse() { return !elseBody.isEmpty(); }

    @Override public String nodeName() { return "IfStatement"; }
    @Override public List<PythonAstNode> children() {
        List<PythonAstNode> out = new ArrayList<>(branches);
        out.addAll(elseBody);
        return out;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitIf(this); }
}
