package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code url_for('endpoint', param=value)}: a Flask-aware node that links the
 * template to a route endpoint of the Python side.
 */
public final class UrlForExpression extends JinjaExpression {
    private final String endpoint;
    private final SourcePosition endpointPosition;
    private final List<JinjaKeywordArgument> parameters;

    public UrlForExpression(SourcePosition position, String endpoint, SourcePosition endpointPosition,
                            List<JinjaKeywordArgument> parameters) {
        super(position);
        this.endpoint = endpoint;
        this.endpointPosition = endpointPosition;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public String endpoint() { return endpoint; }
    public SourcePosition endpointPosition() { return endpointPosition; }
    public List<JinjaKeywordArgument> parameters() { return parameters; }

    public JinjaKeywordArgument parameter(String name) {
        for (JinjaKeywordArgument k : parameters) if (k.name().equals(name)) return k;
        return null;
    }

    public List<String> parameterNames() {
        List<String> out = new ArrayList<>();
        for (JinjaKeywordArgument k : parameters) out.add(k.name());
        return out;
    }

    @Override public String nodeName() { return "UrlForExpression"; }
    // أبناء هذه العقدة هم وسائط الرابط المسماة؛ اسم المسار نفسه مخزن كخاصية وليس ابن عقدة مستقل.
    @Override public List<JinjaKeywordArgument> children() { return parameters; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("endpoint", endpoint);
        p.put("parameters", parameterNames());
        return p;
    }
    @Override public String label() { return "UrlForExpression: " + endpoint + " " + parameterNames(); }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitUrlFor(this); }
}
