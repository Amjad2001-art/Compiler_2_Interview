package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One {@code if}/{@code elif} branch: a condition and its body. */
public final class ConditionalBranch extends PythonAstNode {
    private final String keyword;
    private final PythonExpression condition;
    private final List<PythonStatement> body;

    public ConditionalBranch(SourcePosition position, String keyword, PythonExpression condition, List<PythonStatement> body) {
        super(position);
        this.keyword = keyword;
        this.condition = condition;
        this.body = Collections.unmodifiableList(new ArrayList<>(body));
    }

    public String keyword() { return keyword; }
    public PythonExpression condition() { return condition; }
    public List<PythonStatement> body() { return body; }

    @Override public String nodeName() { return "ConditionalBranch"; }
    @Override public List<PythonAstNode> children() {
        List<PythonAstNode> out = new ArrayList<>();
        out.add(condition);
        out.addAll(body);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("keyword", keyword);
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitConditionalBranch(this); }
}
