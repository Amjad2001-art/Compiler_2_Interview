package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code target|filter(args)}, e.g. {@code products|length} or {@code text|truncate(80)}. */
public final class FilterExpression extends JinjaExpression {
    private final JinjaExpression target;
    private final String filterName;
    private final List<JinjaExpression> arguments;
    private final List<JinjaKeywordArgument> keywordArguments;

    public FilterExpression(SourcePosition position, JinjaExpression target, String filterName,
                            List<JinjaExpression> arguments, List<JinjaKeywordArgument> keywordArguments) {
        super(position);
        this.target = target;
        this.filterName = filterName;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.keywordArguments = Collections.unmodifiableList(new ArrayList<>(keywordArguments));
    }

    public JinjaExpression target() { return target; }
    public String filterName() { return filterName; }
    public List<JinjaExpression> arguments() { return arguments; }
    public List<JinjaKeywordArgument> keywordArguments() { return keywordArguments; }

    @Override public String nodeName() { return "FilterExpression"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>();
        out.add(target);
        out.addAll(arguments);
        out.addAll(keywordArguments);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("filter", filterName);
        return p;
    }
    @Override public String label() { return "FilterExpression: |" + filterName; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitFilter(this); }
}
