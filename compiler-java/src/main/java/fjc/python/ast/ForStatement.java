package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code for a, b in iterable: body} */
public final class ForStatement extends PythonStatement {
    private final List<String> targets;
    private final PythonExpression iterable;
    private final List<PythonStatement> body;

    public ForStatement(SourcePosition position, List<String> targets, PythonExpression iterable, List<PythonStatement> body) {
        super(position);
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        this.iterable = iterable;
        this.body = Collections.unmodifiableList(new ArrayList<>(body));
    }

    public List<String> targets() { return targets; }
    public PythonExpression iterable() { return iterable; }
    public List<PythonStatement> body() { return body; }

    @Override public String nodeName() { return "ForStatement"; }
    @Override public List<PythonAstNode> children() {
        List<PythonAstNode> out = new ArrayList<>();
        out.add(iterable);
        out.addAll(body);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("targets", targets);
        return p;
    }
    @Override public String label() { return "ForStatement: " + String.join(", ", targets) + " in ..."; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitFor(this); }
}
