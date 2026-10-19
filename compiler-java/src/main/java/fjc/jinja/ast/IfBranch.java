package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One {@code if}/{@code elif} branch: condition + body. */
public final class IfBranch extends JinjaAstNode {
    private final String keyword;
    private final JinjaExpression condition;
    private final List<JinjaAstNode> body;

    public IfBranch(SourcePosition position, String keyword, JinjaExpression condition, List<JinjaAstNode> body) {
        super(position);
        this.keyword = keyword;
        this.condition = condition;
        this.body = Collections.unmodifiableList(new ArrayList<>(body));
    }

    public String keyword() { return keyword; }
    public JinjaExpression condition() { return condition; }
    public List<JinjaAstNode> body() { return body; }

    @Override public String nodeName() { return "IfBranch"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>();
        out.add(condition);
        out.addAll(body);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("keyword", keyword);
        return p;
    }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitIfBranch(this); }
}
