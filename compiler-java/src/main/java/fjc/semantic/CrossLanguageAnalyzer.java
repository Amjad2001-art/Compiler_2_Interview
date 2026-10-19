package fjc.semantic;

import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;
import fjc.common.SourcePosition;
import fjc.jinja.ast.ForStatement;
import fjc.jinja.ast.HtmlAttribute;
import fjc.jinja.ast.HtmlElement;
import fjc.jinja.ast.IfBranch;
import fjc.jinja.ast.IfStatement;
import fjc.jinja.ast.JinjaAstNode;
import fjc.jinja.ast.JinjaProject;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.ast.OutputExpression;
import fjc.jinja.ast.UrlForExpression;
import fjc.python.ast.KeywordArgument;

import java.util.List;
import java.util.Map;

/**
 * Checks that only make sense when looking at Python and Jinja together:
 * <ul>
 *   <li>PY004 MissingContextVariable – a route renders a template without a variable the template requires
 *       (reported on the Python side, at the render_template call)</li>
 *   <li>XL001 FormMethodMismatch – a form posts to an endpoint that does not accept POST (or vice versa)</li>
 *   <li>XL002 UnusedContextVariable – a route passes a variable the template never uses (warning)</li>
 *   <li>XL003 TemplateNeverRendered – a template no route renders (warning)</li>
 * </ul>
 */
/* فحص الربط بين Flask وJinja: التأكد من مرور المتغيرات التي تحتاجها القوالب. */
public final class CrossLanguageAnalyzer {
    private final ErrorCollector errors;
    private final FlaskModel model;

    public CrossLanguageAnalyzer(ErrorCollector errors, FlaskModel model) {
        this.errors = errors;
        this.model = model;
    }

    public void analyze(JinjaProject project) {
        for (JinjaTemplate template : project.templates()) analyzeTemplate(template);
    }

    private void analyzeTemplate(JinjaTemplate template) {
        // تستخرج هذه الخطوة المتغيرات الحرة التي يحتاجها القالب، بعيدا عن متغيرات الحلقات المحلية.
        Map<String, SourcePosition> required = TemplateRequirements.freeVariables(template);
        List<FlaskModel.RenderCall> renders = model.rendersOf(template.name());
        Map<String, ValueShape> union = model.unionContext(template.name());

        if (renders.isEmpty()) {
            errors.warning(ErrorCategory.CROSS, "XL003", "TemplateNeverRendered",
                    "Template '" + template.name() + "' is not rendered by any route (no page will be generated for it)",
                    template.position());
        }

        for (FlaskModel.RenderCall rc : renders) {
            for (Map.Entry<String, SourcePosition> req : required.entrySet()) {
                String name = req.getKey();
                if (rc.provides(name)) continue;
                boolean knownInPython = model.pythonVisibleNames(rc.route()).contains(name);
                boolean providedElsewhere = union.containsKey(name);
                if (knownInPython || providedElsewhere) {
                    String hint = providedElsewhere && !knownInPython
                            ? " (other routes pass it)"
                            : " (a Python variable named '" + name + "' exists but is not passed)";
                    // مثال: وجود متغير منتج في دالة بايثون لا يكفي؛ يجب تمريره صراحة إلى القالب.
                    errors.error(ErrorCategory.PYTHON, "PY004", "MissingContextVariable",
                            "Route '" + rc.route().endpoint() + "' renders '" + template.name() + "' without the context variable '"
                                    + name + "' that the template uses at line " + req.getValue().line() + hint,
                            rc.call().position());
                }
                // otherwise the Jinja analyser reported JJ001 UndefinedVariable at the template
            }
            for (KeywordArgument k : rc.call().contextArguments()) {
                if (!required.containsKey(k.name())) {
                    errors.warning(ErrorCategory.CROSS, "XL002", "UnusedContextVariable",
                            "Context variable '" + k.name() + "' passed by route '" + rc.route().endpoint()
                                    + "' is never used by template '" + template.name() + "'", k.position());
                }
            }
        }
        checkForms(template.children());
    }

    private void checkForms(List<? extends JinjaAstNode> nodes) {
        for (JinjaAstNode n : nodes) {
            if (n instanceof HtmlElement) {
                HtmlElement e = (HtmlElement) n;
                if (e.tagName().equals("form")) checkForm(e);
                checkForms(e.body());
            } else if (n instanceof ForStatement) {
                checkForms(((ForStatement) n).body());
            } else if (n instanceof IfStatement) {
                for (IfBranch b : ((IfStatement) n).branches()) checkForms(b.body());
                checkForms(((IfStatement) n).elseBody());
            }
        }
    }

    private void checkForm(HtmlElement form) {
        HtmlAttribute method = form.attribute("method");
        HtmlAttribute action = form.attribute("action");
        if (action == null) return;
        String verb = method == null || method.staticValue() == null ? "GET" : method.staticValue().trim().toUpperCase();
        for (JinjaAstNode part : action.valueParts()) {
            if (part instanceof OutputExpression && ((OutputExpression) part).expression() instanceof UrlForExpression) {
                UrlForExpression u = (UrlForExpression) ((OutputExpression) part).expression();
                FlaskModel.RouteInfo route = model.route(u.endpoint());
                if (route == null) return;   // JJ004 already reported
                if (!route.accepts(verb)) {
                    errors.error(ErrorCategory.CROSS, "XL001", "FormMethodMismatch",
                            "Form submits with method " + verb + " to endpoint '" + u.endpoint() + "' but route '"
                                    + route.rule() + "' only accepts " + route.methods(), form.position());
                }
            }
        }
    }
}
