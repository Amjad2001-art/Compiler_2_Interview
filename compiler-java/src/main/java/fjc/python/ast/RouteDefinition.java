package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Flask route: {@code @app.route(rule, methods=[...])} applied to a function.
 * The AST builder recognises the decorator and produces this node instead of a
 * generic decorated FunctionDefinition, so the semantic analyser and the
 * evaluator can work with routes directly.
 */
public final class RouteDefinition extends PythonStatement {
    private final String rule;
    private final List<String> methods;
    private final FunctionDefinition function;
    private final List<RouteParameter> parameters;
    private final SourcePosition methodsPosition;
    private final String applicationName;

    public RouteDefinition(SourcePosition position, String rule, List<String> methods,
                           SourcePosition methodsPosition, FunctionDefinition function) {
        this(position, rule, methods, methodsPosition, function, "app");
    }

    public RouteDefinition(SourcePosition position, String rule, List<String> methods,
                           SourcePosition methodsPosition, FunctionDefinition function, String applicationName) {
        super(position);
        this.applicationName = applicationName;
        this.rule = rule;
        this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
        this.methodsPosition = methodsPosition;
        this.function = function;
        this.parameters = Collections.unmodifiableList(parseRule(rule));
    }

    /** A path parameter such as {@code <int:product_id>}. */
    public static final class RouteParameter {
        private final String name;
        private final String converter;

        public RouteParameter(String name, String converter) {
            this.name = name;
            this.converter = converter;
        }

        public String name() { return name; }
        public String converter() { return converter; }
        @Override public String toString() { return "<" + converter + ":" + name + ">"; }
    }

    static List<RouteParameter> parseRule(String rule) {
        List<RouteParameter> out = new ArrayList<>();
        int i = 0;
        while (i < rule.length()) {
            int open = rule.indexOf('<', i);
            if (open < 0) break;
            int close = rule.indexOf('>', open);
            if (close < 0) break;
            String inner = rule.substring(open + 1, close);
            String converter = "string";
            String name = inner;
            int colon = inner.indexOf(':');
            if (colon >= 0) {
                converter = inner.substring(0, colon).trim();
                name = inner.substring(colon + 1).trim();
            }
            out.add(new RouteParameter(name, converter));
            i = close + 1;
        }
        return out;
    }

    public String rule() { return rule; }
    /** The object the decorator was applied on, e.g. {@code app} in {@code @app.route}. */
    public String applicationName() { return applicationName; }
    public List<String> methods() { return methods; }
    public SourcePosition methodsPosition() { return methodsPosition; }
    public FunctionDefinition function() { return function; }
    public String endpoint() { return function.name(); }
    public List<RouteParameter> parameters() { return parameters; }

    public boolean accepts(String method) {
        return methods.contains(method.toUpperCase());
    }

    @Override public String nodeName() { return "RouteDefinition"; }
    @Override public List<FunctionDefinition> children() { return List.of(function); }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("rule", rule);
        p.put("methods", methods);
        p.put("endpoint", endpoint());
        return p;
    }
    @Override public String label() { return "RouteDefinition: " + rule + " " + methods + " -> " + endpoint(); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitRouteDefinition(this); }
}
