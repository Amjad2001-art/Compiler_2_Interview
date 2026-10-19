package fjc.semantic;

import fjc.python.ast.FunctionDefinition;
import fjc.python.ast.RenderTemplateCall;
import fjc.python.ast.RouteDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the Python/Flask semantic analysis learned about the application:
 * routes (endpoints, rules, methods, parameters), which template each route
 * renders and with which context shapes. The Jinja and cross-language
 * analysers and the URL resolver of the generator consume this model.
 */
public final class FlaskModel {

    /** One route/endpoint. */
    public static final class RouteInfo {
        private final RouteDefinition definition;
        private final List<RenderCall> renderCalls = new ArrayList<>();
        private final Set<String> localNames = new LinkedHashSet<>();

        RouteInfo(RouteDefinition definition) { this.definition = definition; }

        public RouteDefinition definition() { return definition; }
        public FunctionDefinition function() { return definition.function(); }
        public String endpoint() { return definition.endpoint(); }
        public String rule() { return definition.rule(); }
        public List<String> methods() { return definition.methods(); }
        public List<RouteDefinition.RouteParameter> parameters() { return definition.parameters(); }
        public List<RenderCall> renderCalls() { return Collections.unmodifiableList(renderCalls); }
        public Set<String> localNames() { return Collections.unmodifiableSet(localNames); }
        public boolean accepts(String method) { return definition.accepts(method); }
        public boolean rendersTemplates() { return !renderCalls.isEmpty(); }

        public List<String> parameterNames() {
            List<String> out = new ArrayList<>();
            for (RouteDefinition.RouteParameter p : parameters()) out.add(p.name());
            return out;
        }
    }

    /** One render_template(...) call inside a route, with the inferred context shapes. */
    public static final class RenderCall {
        private final RouteInfo route;
        private final RenderTemplateCall call;
        private final Map<String, ValueShape> context;

        RenderCall(RouteInfo route, RenderTemplateCall call, Map<String, ValueShape> context) {
            this.route = route;
            this.call = call;
            this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
        }

        public RouteInfo route() { return route; }
        public RenderTemplateCall call() { return call; }
        public String templateName() { return call.templateName(); }
        public Map<String, ValueShape> context() { return context; }
        public boolean provides(String name) { return context.containsKey(name); }
    }

    private final List<RouteInfo> routes = new ArrayList<>();
    private final Map<String, RouteInfo> byEndpoint = new LinkedHashMap<>();
    private final List<String> templateFiles;
    private final List<String> staticFiles;
    private final Set<String> moduleNames = new LinkedHashSet<>();

    public FlaskModel(List<String> templateFiles, List<String> staticFiles) {
        this.templateFiles = new ArrayList<>(templateFiles);
        this.staticFiles = new ArrayList<>(staticFiles);
    }

    RouteInfo addRoute(RouteDefinition definition) {
        RouteInfo info = new RouteInfo(definition);
        routes.add(info);
        byEndpoint.putIfAbsent(info.endpoint(), info);
        return info;
    }

    void addRenderCall(RouteInfo route, RenderTemplateCall call, Map<String, ValueShape> context) {
        route.renderCalls.add(new RenderCall(route, call, context));
    }

    void addLocalName(RouteInfo route, String name) { route.localNames.add(name); }
    void addModuleName(String name) { moduleNames.add(name); }

    public List<RouteInfo> routes() { return Collections.unmodifiableList(routes); }
    public RouteInfo route(String endpoint) { return byEndpoint.get(endpoint); }
    public boolean hasEndpoint(String endpoint) { return endpoint.equals("static") || byEndpoint.containsKey(endpoint); }
    public List<String> templateFiles() { return Collections.unmodifiableList(templateFiles); }
    public List<String> staticFiles() { return Collections.unmodifiableList(staticFiles); }
    public boolean hasTemplate(String name) { return templateFiles.contains(name); }
    public boolean hasStaticFile(String name) { return staticFiles.contains(name.replace('\\', '/')); }
    public Set<String> moduleNames() { return Collections.unmodifiableSet(moduleNames); }

    /** Every render call of every route, in source order. */
    public List<RenderCall> renderCalls() {
        List<RenderCall> out = new ArrayList<>();
        for (RouteInfo r : routes) out.addAll(r.renderCalls);
        return out;
    }

    /** The render calls that target the given template. */
    public List<RenderCall> rendersOf(String templateName) {
        List<RenderCall> out = new ArrayList<>();
        for (RenderCall rc : renderCalls()) if (rc.templateName().equals(templateName)) out.add(rc);
        return out;
    }

    /** Union of the context shapes provided by all routes rendering the template. */
    public Map<String, ValueShape> unionContext(String templateName) {
        Map<String, ValueShape> out = new LinkedHashMap<>();
        for (RenderCall rc : rendersOf(templateName)) {
            for (Map.Entry<String, ValueShape> e : rc.context().entrySet()) {
                out.merge(e.getKey(), e.getValue(), ValueShape::union);
            }
        }
        return out;
    }

    /** Names visible in Python at the call site of the given route: module names plus its locals. */
    public Set<String> pythonVisibleNames(RouteInfo route) {
        Set<String> out = new LinkedHashSet<>(moduleNames);
        if (route != null) out.addAll(route.localNames);
        return out;
    }

    /** Endpoints that render a template (the pages the generator will produce). */
    public List<RouteInfo> pageRoutes() {
        List<RouteInfo> out = new ArrayList<>();
        for (RouteInfo r : routes) if (r.rendersTemplates()) out.add(r);
        return out;
    }
}
