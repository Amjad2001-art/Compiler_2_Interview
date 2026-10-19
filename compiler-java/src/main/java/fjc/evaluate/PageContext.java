package fjc.evaluate;

import fjc.semantic.FlaskModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One page instance to generate: a route (with concrete URL parameter values)
 * rendered a template with this context. A route without URL parameters gives
 * exactly one PageContext; a parameterized route (e.g. /product/&lt;int:product_id&gt;)
 * gives one per data item.
 */
public final class PageContext {
    private final FlaskModel.RouteInfo route;
    private final String templateName;
    private final Map<String, Value> routeParameters;
    private final Map<String, Value> context;

    public PageContext(FlaskModel.RouteInfo route, String templateName, Map<String, Value> routeParameters, Map<String, Value> context) {
        this.route = route;
        this.templateName = templateName;
        this.routeParameters = Collections.unmodifiableMap(new LinkedHashMap<>(routeParameters));
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public FlaskModel.RouteInfo route() { return route; }
    public String endpoint() { return route.endpoint(); }
    public String templateName() { return templateName; }
    public Map<String, Value> routeParameters() { return routeParameters; }
    public Map<String, Value> context() { return context; }
    public boolean isParameterized() { return !routeParameters.isEmpty(); }

    /** Query-string form of the parameters, e.g. {@code product_id=2} (used as page instance key). */
    public String queryString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Value> e : routeParameters.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue().asText());
        }
        return sb.toString();
    }

    /** Concrete runtime URL, e.g. {@code /product/2}. */
    public String runtimeUrl() {
        String url = route.rule();
        for (Map.Entry<String, Value> e : routeParameters.entrySet()) {
            url = url.replaceAll("<(?:[a-z]+:)?" + e.getKey() + ">", e.getValue().asText());
        }
        return url;
    }

    @Override public String toString() {
        return endpoint() + (routeParameters.isEmpty() ? "" : "(" + queryString() + ")") + " -> " + templateName + " " + context.keySet();
    }
}
