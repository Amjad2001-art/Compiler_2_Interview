package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code {% for a, b in iterable %} body {% endfor %}} */
public final class ForStatement extends JinjaAstNode {
    private final List<String> loopVariables;
    private final JinjaExpression iterable;
    private final List<JinjaAstNode> body;

    public ForStatement(SourcePosition position, List<String> loopVariables, JinjaExpression iterable, List<JinjaAstNode> body) {
        super(position);
        this.loopVariables = Collections.unmodifiableList(new ArrayList<>(loopVariables));
        this.iterable = iterable;
        this.body = Collections.unmodifiableList(new ArrayList<>(body));
    }

    public List<String> loopVariables() { return loopVariables; }
    public JinjaExpression iterable() { return iterable; }
    public List<JinjaAstNode> body() { return body; }

    @Override public String nodeName() { return "ForStatement"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>();
        out.add(iterable);
        out.addAll(body);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("loopVariables", loopVariables);
        return p;
    }
    @Override public String label() { return "ForStatement: " + String.join(", ", loopVariables) + " in ..."; }
    // هنا توجّه عقدة الحلقة الزائر إلى تابع الحلقة تحديدا؛ هذه الصلة العملية بين العقد والزائر.
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitFor(this); }
}
