package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code target is [not] test(args)}, e.g. {@code product.image is defined}. */
public final class TestExpression extends JinjaExpression {
    private final JinjaExpression target;
    private final String testName;
    private final boolean negated;
    private final List<JinjaExpression> arguments;

    public TestExpression(SourcePosition position, JinjaExpression target, String testName, boolean negated,
                          List<JinjaExpression> arguments) {
        super(position);
        this.target = target;
        this.testName = testName;
        this.negated = negated;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public JinjaExpression target() { return target; }
    public String testName() { return testName; }
    public boolean isNegated() { return negated; }
    public List<JinjaExpression> arguments() { return arguments; }

    @Override public String nodeName() { return "TestExpression"; }
    @Override public List<JinjaExpression> children() {
        List<JinjaExpression> out = new ArrayList<>();
        out.add(target);
        out.addAll(arguments);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("test", testName);
        p.put("negated", negated);
        return p;
    }
    @Override public String label() { return "TestExpression: is " + (negated ? "not " : "") + testName; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitTest(this); }
}
