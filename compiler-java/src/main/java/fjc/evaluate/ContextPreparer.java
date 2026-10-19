package fjc.evaluate;

import fjc.python.ast.PythonProgram;
import fjc.python.ast.RouteDefinition;
import fjc.semantic.FlaskModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context/data preparation stage: runs the validated Python program with the
 * {@link Evaluator} and produces one {@link PageContext} per page instance.
 *
 * <p>Parameterized routes (e.g. {@code /product/<int:product_id>}) are
 * instantiated with every identifier found in the module-level data (the
 * {@code id} fields of dictionaries inside module-level lists). Instances whose
 * route aborts (404) or redirects produce no page.
 */
public final class ContextPreparer {
    private final FlaskModel model;

    public ContextPreparer(FlaskModel model) { this.model = model; }

    // تجهيز بيانات سياق الصفحات من Python AST لتمرير القيم إلى قوالب Jinja.
    public ContextData prepare(PythonProgram program) {
        // يُقيَّم الجزء المدعوم من تعليمات بايثون لاستخراج بيانات المنتجات؛ لا نستعمل جدول الرموز هنا.
        Evaluator evaluator = new Evaluator(model);
        evaluator.runModule(program);
        ContextData data = new ContextData();

        for (Map.Entry<String, Value> e : evaluator.globals().variables().entrySet()) {
            if (e.getValue() instanceof ListValue || e.getValue() instanceof DictValue) data.putModuleData(e.getKey(), e.getValue());
        }
        List<Value> candidateIds = collectIds(evaluator.globals());
        data.note("Candidate identifiers for parameterized routes: " + describe(candidateIds));

        // تمر العملية على مسارات Flask التي تعرض قوالب لتجهيز سياق كل صفحة مطلوبة.
        for (FlaskModel.RouteInfo route : model.routes()) {
            if (!route.rendersTemplates()) {
                data.note("Endpoint '" + route.endpoint() + "' (" + route.rule() + ") does not render a template: runtime-only action, no page generated");
                continue;
            }
            Value fn = evaluator.globals().get(route.endpoint());
            if (!(fn instanceof FunctionValue)) {
                data.note("Endpoint '" + route.endpoint() + "' is not bound to a function at evaluation time; skipped");
                continue;
            }
            List<RouteDefinition.RouteParameter> params = route.parameters();
            if (params.isEmpty()) {
                List<RenderInvocation> renders = evaluator.callRoute((FunctionValue) fn, new LinkedHashMap<>());
                addPages(data, route, new LinkedHashMap<>(), renders);
                continue;
            }
            if (params.size() > 1) {
                data.note("Endpoint '" + route.endpoint() + "' has " + params.size() + " URL parameters; multi-parameter routes are not instantiated");
                continue;
            }
            RouteDefinition.RouteParameter p = params.get(0);
            int instances = 0;
            for (Value id : candidateIds) {
                Value converted = convert(id, p.converter());
                if (converted == null) continue;
                Map<String, Value> args = new LinkedHashMap<>();
                args.put(p.name(), converted);
                List<RenderInvocation> renders;
                try {
                    renders = evaluator.callRoute((FunctionValue) fn, args);
                } catch (Evaluator.AbortSignal abort) {
                    data.note("Endpoint '" + route.endpoint() + "' aborted with " + abort.code() + " for " + p.name() + "=" + converted.asText());
                    continue;
                }
                if (addPages(data, route, args, renders)) instances++;
            }
            data.note("Endpoint '" + route.endpoint() + "' (" + route.rule() + ") instantiated " + instances + " time(s)");
        }
        return data;
    }

    private boolean addPages(ContextData data, FlaskModel.RouteInfo route, Map<String, Value> params, List<RenderInvocation> renders) {
        if (renders.isEmpty()) {
            data.note("Endpoint '" + route.endpoint() + "'" + (params.isEmpty() ? "" : " with " + params)
                    + " returned without rendering a template (redirect?); no page generated");
            return false;
        }
        for (RenderInvocation r : renders) {
            // يحفظ سياق الصفحة اسم القالب وقيمه ومعاملات المسار؛ هذا ما يتسلمه مولد HTML.
            data.addPage(new PageContext(route, r.templateName(), params, r.context()));
        }
        return true;
    }

    private static Value convert(Value id, String converter) {
        switch (converter) {
            case "int":
                return id instanceof NumberValue && ((NumberValue) id).isInteger() ? id : null;
            case "float":
                return id instanceof NumberValue ? NumberValue.ofDouble(((NumberValue) id).doubleValue()) : null;
            default:
                return new StringValue(id.asText());
        }
    }

    /** All {@code id} values of dictionaries stored in module-level lists (in order, without duplicates). */
    static List<Value> collectIds(Environment globals) {
        List<Value> ids = new ArrayList<>();
        for (Value v : globals.variables().values()) {
            if (!(v instanceof ListValue)) continue;
            for (Value item : ((ListValue) v).items()) {
                if (item instanceof DictValue && ((DictValue) item).has("id")) {
                    Value id = ((DictValue) item).get("id");
                    boolean dup = false;
                    for (Value x : ids) if (x.equalsValue(id)) dup = true;
                    if (!dup) ids.add(id);
                }
            }
        }
        return ids;
    }

    private static String describe(List<Value> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i).asText());
        }
        return sb.append(']').toString();
    }
}
