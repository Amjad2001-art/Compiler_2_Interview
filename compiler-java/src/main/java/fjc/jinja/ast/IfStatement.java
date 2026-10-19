package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code {% if %} ... {% elif %} ... {% else %} ... {% endif %}} (also used at attribute level). */
public final class IfStatement extends JinjaAstNode {
    private final List<IfBranch> branches;
    private final List<JinjaAstNode> elseBody;

    public IfStatement(SourcePosition position, List<IfBranch> branches, List<JinjaAstNode> elseBody) {
        super(position);
        this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
        this.elseBody = elseBody == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(elseBody));
    }

    public List<IfBranch> branches() { return branches; }
    public List<JinjaAstNode> elseBody() { return elseBody; }
    public boolean hasElse() { return !elseBody.isEmpty(); }

    @Override public String nodeName() { return "IfStatement"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>(branches);
        out.addAll(elseBody);
        return out;
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitIf(this); }
}
