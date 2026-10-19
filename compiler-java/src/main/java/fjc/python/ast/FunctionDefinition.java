package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code def name(params): body}, possibly with non-route decorators. */
public final class FunctionDefinition extends PythonStatement {
    private final String name;
    private final List<Parameter> parameters;
    private final List<PythonStatement> body;
    private final List<PythonExpression> decorators;

    public FunctionDefinition(SourcePosition position, String name, List<Parameter> parameters,
                              List<PythonStatement> body, List<PythonExpression> decorators) {
        super(position);
        this.name = name;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.body = Collections.unmodifiableList(new ArrayList<>(body));
        this.decorators = Collections.unmodifiableList(new ArrayList<>(decorators));
    }

    public String name() { return name; }
    public List<Parameter> parameters() { return parameters; }
    public List<PythonStatement> body() { return body; }
    public List<PythonExpression> decorators() { return decorators; }

    public List<String> parameterNames() {
        List<String> out = new ArrayList<>();
        for (Parameter p : parameters) out.add(p.name());
        return out;
    }

    @Override public String nodeName() { return "FunctionDefinition"; }
    @Override public List<PythonAstNode> children() {
        List<PythonAstNode> out = new ArrayList<>(decorators);
        out.addAll(parameters);
        out.addAll(body);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("name", name);
        return p;
    }
    @Override public String label() { return "FunctionDefinition: " + name + "(" + String.join(", ", parameterNames()) + ")"; }
    // عقدة الدالة توجه زائر بايثون إلى منطق الدوال، بينما العقد الأخرى توجهه إلى توابع مختلفة.
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitFunctionDefinition(this); }
}
