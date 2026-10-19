package fjc.codegen;

import fjc.common.SourcePosition;
import fjc.evaluate.Value;
import fjc.semantic.FlaskModel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves {@code url_for(...)} for the generated static site.
 *
 * <ul>
 *   <li>{@code url_for('static', filename='style.css')} → {@code style.css} (the file is copied next to the pages)</li>
 *   <li>an endpoint that renders a template → the generated page, with URL parameters as a query string
 *       ({@code product_detail.html?product_id=2}); this is what keeps the generated pages connected</li>
 *   <li>inside a form {@code action} attribute, or for endpoints that do not render a page (POST actions such as
 *       delete) → the Flask runtime URL ({@code /delete/2}); static HTML cannot mutate data, the running
 *       Flask application performs the change and regenerates the site</li>
 * </ul>
 */
public final class UrlResolver {
    private final FlaskModel model;
    private final Map<String, String> pageFiles;

    public UrlResolver(FlaskModel model, Map<String, String> pageFilesByEndpoint) {
        this.model = model;
        this.pageFiles = Collections.unmodifiableMap(new LinkedHashMap<>(pageFilesByEndpoint));
    }

    public Map<String, String> pageFiles() { return pageFiles; }

    public String resolve(String endpoint, Map<String, Value> params, boolean forFormAction, SourcePosition pos) {
        if (endpoint.equals("static")) {
            Value filename = params.get("filename");
            if (filename == null) throw new GenerationException("GEN007", "UrlForError", "url_for('static') needs filename=", pos);
            return filename.asText();
        }
        FlaskModel.RouteInfo route = model.route(endpoint);
        if (route == null) {
            throw new GenerationException("GEN007", "UrlForError", "Unknown endpoint '" + endpoint + "'", pos);
        }
        String page = pageFiles.get(endpoint);
        if (page != null && !forFormAction) {
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, Value> e : params.entrySet()) {
                query.append(query.length() == 0 ? '?' : '&').append(e.getKey()).append('=')
                        .append(java.net.URLEncoder.encode(e.getValue().asText(), java.nio.charset.StandardCharsets.UTF_8));
            }
            return page + query;
        }
        return runtimeUrl(route, params);
    }

    /** The URL the running Flask application serves, e.g. {@code /product/2}. */
    public static String runtimeUrl(FlaskModel.RouteInfo route, Map<String, Value> params) {
        String url = route.rule();
        for (Map.Entry<String, Value> e : params.entrySet()) {
            url = url.replaceAll("<(?:[a-z]+:)?" + e.getKey() + ">", java.util.regex.Matcher.quoteReplacement(e.getValue().asText()));
        }
        return url;
    }
}
