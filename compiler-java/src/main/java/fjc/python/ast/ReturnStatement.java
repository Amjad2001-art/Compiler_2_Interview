package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.Collections;
import java.util.List;

/** {@code return [value]} */
public final class ReturnStatement extends PythonStatement {
    private final PythonExpression value;

    public ReturnStatement(SourcePosition position, PythonExpression value) {
        super(position);
        this.value = value;
    }

    public PythonExpression value() { return value; }

    @Override public String nodeName() { return "ReturnStatement"; }
    @Override public List<PythonExpression> children() {
        return value == null ? Collections.emptyList() : List.of(value);
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitReturn(this); }
}
