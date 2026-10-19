package fjc.evaluate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A recorded {@code render_template(name, **context)} call reached while evaluating a route. */
public final class RenderInvocation {
    private final String templateName;
    private final Map<String, Value> context;

    public RenderInvocation(String templateName, Map<String, Value> context) {
        this.templateName = templateName;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public String templateName() { return templateName; }
    public Map<String, Value> context() { return context; }
}
