package fjc.codegen;

import fjc.common.SourcePosition;
import fjc.evaluate.BooleanValue;
import fjc.evaluate.BuiltinFunction;
import fjc.evaluate.DictValue;
import fjc.evaluate.Evaluator;
import fjc.evaluate.ListValue;
import fjc.evaluate.NoneValue;
import fjc.evaluate.NumberValue;
import fjc.evaluate.ObjectValue;
import fjc.evaluate.PageContext;
import fjc.evaluate.StringValue;
import fjc.evaluate.Value;
import fjc.jinja.ast.FilterExpression;
import fjc.jinja.ast.ForStatement;
import fjc.jinja.ast.FunctionCall;
import fjc.jinja.ast.HtmlAttribute;
import fjc.jinja.ast.HtmlComment;
import fjc.jinja.ast.HtmlDoctype;
import fjc.jinja.ast.HtmlElement;
import fjc.jinja.ast.IfBranch;
import fjc.jinja.ast.IfStatement;
import fjc.jinja.ast.IndexAccess;
import fjc.jinja.ast.JinjaAstNode;
import fjc.jinja.ast.JinjaAstVisitor;
import fjc.jinja.ast.JinjaBinaryExpression;
import fjc.jinja.ast.JinjaComparisonExpression;
import fjc.jinja.ast.JinjaExpression;
import fjc.jinja.ast.JinjaKeywordArgument;
import fjc.jinja.ast.JinjaListLiteral;
import fjc.jinja.ast.JinjaLiteral;
import fjc.jinja.ast.JinjaLogicalExpression;
import fjc.jinja.ast.JinjaProject;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.ast.JinjaUnaryExpression;
import fjc.jinja.ast.OutputExpression;
import fjc.jinja.ast.PropertyAccess;
import fjc.jinja.ast.TestExpression;
import fjc.jinja.ast.TextNode;
import fjc.jinja.ast.UrlForExpression;
import fjc.jinja.ast.VariableReference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a Jinja AST with concrete context values into HTML. Structure nodes
 * are handled by this visitor; expressions by the nested {@link ExpressionEvaluator}
 * visitor. Output is autoescaped unless a value is marked safe.
 *
 * <p>The renderer works only with the validated AST and the prepared context
 * data. It has no dependency on the symbol table.
 */
public final class TemplateRenderer implements JinjaAstVisitor<Void> {

    private final UrlResolver urls;
    private final Evaluator runtime;
    private final JinjaFilters filters;
    private final ExpressionEvaluator expressions = new ExpressionEvaluator();
    private final Deque<Map<String, Value>> scopes = new ArrayDeque<>();
    private StringBuilder out = new StringBuilder();
    private boolean inFormAction = false;
    private HtmlElement currentElement = null;

    public TemplateRenderer(UrlResolver urls) {
        this.urls = urls;
        this.runtime = new Evaluator(null);
        this.filters = new JinjaFilters(runtime);
    }

    // ---------------------------------------------------------------- driving

    /** Renders the template once with the given context. */
    // يدخل السياق ثم يزور أبناء شجرة جينجا؛ القيم تُقيَّم وقت التوليد ولا تُضاف كعقد جديدة.
    public String render(JinjaTemplate template, Map<String, Value> context) {
        out = new StringBuilder();
        pushContext(context);
        renderNodes(template.children());
        scopes.pop();
        return out.toString();
    }

    /**
     * Renders a parameterized page: the document skeleton once, the {@code <body>}
     * content once per page instance wrapped in {@code <div class="page-instance" data-page-key=...>}.
     * The copied script.js shows the instance whose key matches the query string.
     */
    public String renderInstances(JinjaTemplate template, List<PageContext> pages) {
        if (pages.size() == 1) return render(template, pages.get(0).context());
        out = new StringBuilder();
        pushContext(pages.get(0).context());
        renderMulti(template.children(), template, pages);
        scopes.pop();
        return out.toString();
    }

    private void renderMulti(List<? extends JinjaAstNode> nodes, JinjaTemplate template, List<PageContext> pages) {
        for (JinjaAstNode n : nodes) {
            if (n instanceof HtmlElement && ((HtmlElement) n).tagName().equals("html")) {
                HtmlElement html = (HtmlElement) n;
                renderStartTag(html);
                renderMulti(html.body(), template, pages);
                out.append("</html>");
            } else if (n instanceof HtmlElement && ((HtmlElement) n).tagName().equals("body")) {
                HtmlElement body = (HtmlElement) n;
                renderStartTag(body);
                // Scripts shared by all instances must run only once, AFTER all
                // page instances have been rendered into the HTML document.
                // Otherwise script.js executes on a partial DOM and reports a
                // false "product_id not found" for products later in the page.
                List<JinjaAstNode> perInstanceNodes = new ArrayList<>();
                List<JinjaAstNode> sharedScripts = new ArrayList<>();
                for (JinjaAstNode child : body.body()) {
                    if (isSharedExternalScript(child)) {
                        sharedScripts.add(child);
                    } else {
                        perInstanceNodes.add(child);
                    }
                }
                for (int i = 0; i < pages.size(); i++) {
                    PageContext page = pages.get(i);
                    String title = pageTitle(template, page.context());
                    out.append("\n<div class=\"page-instance\" data-page-key=\"").append(HtmlEscaper.escape(page.queryString()))
                            .append("\" data-page-title=\"").append(HtmlEscaper.escape(title)).append('"')
                            .append(i == 0 ? "" : " hidden").append(">\n");
                    pushContext(page.context());
                    renderNodes(perInstanceNodes);
                    scopes.pop();
                    out.append("\n</div>\n");
                }
                renderNodes(sharedScripts);
                out.append("</body>");
            } else {
                n.accept(this);
            }
        }
    }

    /** Only hoist support scripts whose URLs are independent of product context. */
    private static boolean isSharedExternalScript(JinjaAstNode child) {
        if (!(child instanceof HtmlElement)) return false;
        HtmlElement element = (HtmlElement) child;
        if (!element.tagName().equals("script")) return false;
        HtmlAttribute src = element.attribute("src");
        if (src == null || !src.hasValue()) return false;
        for (JinjaAstNode part : src.valueParts()) {
            if (part instanceof TextNode) continue;
            if (part instanceof OutputExpression) {
                JinjaExpression expr = ((OutputExpression) part).expression();
                if (expr instanceof UrlForExpression && ((UrlForExpression) expr).endpoint().equals("static")) continue;
            }
            return false;
        }
        return true;
    }

    private String pageTitle(JinjaTemplate template, Map<String, Value> context) {
        HtmlElement title = findElement(template.children(), "title");
        if (title == null) return "";
        StringBuilder saved = out;
        out = new StringBuilder();
        pushContext(context);
        renderNodes(title.body());
        scopes.pop();
        String text = out.toString().trim();
        out = saved;
        return text.replace("&#34;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static HtmlElement findElement(List<? extends JinjaAstNode> nodes, String tag) {
        for (JinjaAstNode n : nodes) {
            if (n instanceof HtmlElement) {
                HtmlElement e = (HtmlElement) n;
                if (e.tagName().equals(tag)) return e;
                HtmlElement found = findElement(e.body(), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void pushContext(Map<String, Value> context) {
        Map<String, Value> scope = new LinkedHashMap<>();
        ObjectValue request = new ObjectValue("request");
        request.set("method", new StringValue("GET"));
        request.set("path", new StringValue("/"));
        request.set("url", new StringValue("/"));
        request.set("endpoint", new StringValue(""));
        request.set("args", new DictValue());
        request.set("form", new DictValue());
        scope.put("request", request);
        scope.put("config", new DictValue());
        scope.putAll(context);
        scopes.push(scope);
    }

    private Value lookup(String name) {
        for (Map<String, Value> s : scopes) if (s.containsKey(name)) return s.get(name);
        return null;
    }

    // هذا المرور يطلب من كل عقدة توجيه الزائر إلى التابع الذي يطابق نوعها.
    private void renderNodes(List<? extends JinjaAstNode> nodes) {
        for (JinjaAstNode n : nodes) n.accept(this);
    }

    // ------------------------------------------------------------- structure

    @Override public Void visitProject(JinjaProject node) { throw new IllegalStateException("render templates individually"); }
    @Override public Void visitTemplate(JinjaTemplate node) { renderNodes(node.children()); return null; }
    @Override public Void visitText(TextNode node) { out.append(node.text()); return null; }
    @Override public Void visitDoctype(HtmlDoctype node) { out.append("<!").append(node.text()).append('>'); return null; }
    @Override public Void visitComment(HtmlComment node) { out.append("<!--").append(node.text()).append("-->"); return null; }

    @Override public Void visitElement(HtmlElement node) {
        renderStartTag(node);
        if (node.isVoid()) return null;
        renderNodes(node.body());
        out.append("</").append(node.tagName()).append('>');
        return null;
    }

    private void renderStartTag(HtmlElement node) {
        HtmlElement savedElement = currentElement;
        currentElement = node;
        out.append('<').append(node.tagName());
        renderAttributeItems(node.attributes());
        out.append('>');
        currentElement = savedElement;
    }

    private void renderAttributeItems(List<? extends JinjaAstNode> items) {
        for (JinjaAstNode item : items) {
            if (item instanceof HtmlAttribute) {
                item.accept(this);
            } else if (item instanceof OutputExpression) {
                Value v = expressions.eval(((OutputExpression) item).expression());
                String text = v.isNone() ? "" : v.asText();
                if (!text.isEmpty()) out.append(' ').append(v instanceof StringValue && ((StringValue) v).isSafe() ? text : HtmlEscaper.escape(text));
            } else if (item instanceof IfStatement) {
                IfStatement i = (IfStatement) item;
                boolean done = false;
                for (IfBranch b : i.branches()) {
                    if (expressions.eval(b.condition()).isTruthy()) { renderAttributeItems(b.body()); done = true; break; }
                }
                if (!done) renderAttributeItems(i.elseBody());
            } else if (item instanceof ForStatement) {
                ForStatement f = (ForStatement) item;
                forEach(f, () -> renderAttributeItems(f.body()));
            } else {
                item.accept(this);
            }
        }
    }

    @Override public Void visitAttribute(HtmlAttribute node) {
        out.append(' ').append(node.name());
        if (!node.hasValue()) return null;
        out.append("=\"");
        boolean savedFlag = inFormAction;
        inFormAction = currentElement != null && currentElement.tagName().equals("form") && node.name().equalsIgnoreCase("action");
        renderValueParts(node.valueParts());
        inFormAction = savedFlag;
        out.append('"');
        return null;
    }

    private void renderValueParts(List<? extends JinjaAstNode> parts) {
        for (JinjaAstNode part : parts) {
            if (part instanceof TextNode) {
                out.append(((TextNode) part).text());
            } else if (part instanceof OutputExpression) {
                appendOutput(expressions.eval(((OutputExpression) part).expression()));
            } else if (part instanceof IfStatement) {
                IfStatement i = (IfStatement) part;
                boolean done = false;
                for (IfBranch b : i.branches()) {
                    if (expressions.eval(b.condition()).isTruthy()) { renderValueParts(b.body()); done = true; break; }
                }
                if (!done) renderValueParts(i.elseBody());
            } else if (part instanceof ForStatement) {
                ForStatement f = (ForStatement) part;
                forEach(f, () -> renderValueParts(f.body()));
            } else {
                part.accept(this);
            }
        }
    }

    private void appendOutput(Value v) {
        if (v instanceof StringValue && ((StringValue) v).isSafe()) out.append(((StringValue) v).value());
        else out.append(HtmlEscaper.escape(v.asText()));
    }

    // تعبير الإخراج يستبدل قيمة المتغير من السياق داخل النص الناتج مع مراعاة الهروب الآمن.
    @Override public Void visitOutput(OutputExpression node) {
        appendOutput(expressions.eval(node.expression()));
        return null;
    }

    // زيارة الحلقة تكرر جسمها لكل عنصر من القائمة وتضع متغير الحلقة في نطاق محلي.
    @Override public Void visitFor(ForStatement node) {
        forEach(node, () -> renderNodes(node.body()));
        return null;
    }

    // تنفيذ حلقة Jinja على قيم السياق مع إنشاء نطاق مستقل لمتغيرات التكرار.
    private void forEach(ForStatement node, Runnable body) {
        Value iterable = expressions.eval(node.iterable());
        List<Value> items = runtime.iterateValue(iterable, node.iterable().position());
        for (int i = 0; i < items.size(); i++) {
            Map<String, Value> scope = new LinkedHashMap<>();
            Value item = items.get(i);
            List<String> vars = node.loopVariables();
            if (vars.size() == 1) {
                scope.put(vars.get(0), item);
            } else {
                List<Value> parts = runtime.iterateValue(item, node.position());
                if (parts.size() != vars.size()) {
                    throw new GenerationException("GEN008", "UnpackError", "cannot unpack " + parts.size() + " values into " + vars.size() + " loop variables", node.position());
                }
                for (int j = 0; j < vars.size(); j++) scope.put(vars.get(j), parts.get(j));
            }
            scope.put("loop", loopObject(i, items.size()));
            scopes.push(scope);
            body.run();
            scopes.pop();
        }
    }

    private static ObjectValue loopObject(int index0, int length) {
        ObjectValue loop = new ObjectValue("loop");
        loop.set("index", NumberValue.ofLong(index0 + 1));
        loop.set("index0", NumberValue.ofLong(index0));
        loop.set("revindex", NumberValue.ofLong(length - index0));
        loop.set("revindex0", NumberValue.ofLong(length - index0 - 1));
        loop.set("first", BooleanValue.of(index0 == 0));
        loop.set("last", BooleanValue.of(index0 == length - 1));
        loop.set("length", NumberValue.ofLong(length));
        loop.set("depth", NumberValue.ofLong(1));
        loop.set("depth0", NumberValue.ofLong(0));
        loop.set("previtem", NoneValue.NONE);
        loop.set("nextitem", NoneValue.NONE);
        loop.set("cycle", new BuiltinFunction("cycle", (a, k) -> a.isEmpty() ? NoneValue.NONE : a.get(index0 % a.size())));
        loop.set("changed", new BuiltinFunction("changed", (a, k) -> BooleanValue.TRUE));
        return loop;
    }

    // زيارة الشرط تختار الفرع المناسب وفقا لقيمة التعبير، فيظهر المحتوى الموافق فقط.
    @Override public Void visitIf(IfStatement node) {
        for (IfBranch b : node.branches()) {
            if (expressions.eval(b.condition()).isTruthy()) { renderNodes(b.body()); return null; }
        }
        renderNodes(node.elseBody());
        return null;
    }

    @Override public Void visitIfBranch(IfBranch node) { renderNodes(node.body()); return null; }

    // expressions never reach the structure visitor
    @Override public Void visitVariable(VariableReference node) { throw expressionMisuse(node); }
    @Override public Void visitPropertyAccess(PropertyAccess node) { throw expressionMisuse(node); }
    @Override public Void visitIndexAccess(IndexAccess node) { throw expressionMisuse(node); }
    @Override public Void visitFilter(FilterExpression node) { throw expressionMisuse(node); }
    @Override public Void visitTest(TestExpression node) { throw expressionMisuse(node); }
    @Override public Void visitFunctionCall(FunctionCall node) { throw expressionMisuse(node); }
    @Override public Void visitUrlFor(UrlForExpression node) { throw expressionMisuse(node); }
    @Override public Void visitLiteral(JinjaLiteral node) { throw expressionMisuse(node); }
    @Override public Void visitListLiteral(JinjaListLiteral node) { throw expressionMisuse(node); }
    @Override public Void visitBinary(JinjaBinaryExpression node) { throw expressionMisuse(node); }
    @Override public Void visitComparison(JinjaComparisonExpression node) { throw expressionMisuse(node); }
    @Override public Void visitLogical(JinjaLogicalExpression node) { throw expressionMisuse(node); }
    @Override public Void visitUnary(JinjaUnaryExpression node) { throw expressionMisuse(node); }
    @Override public Void visitKeywordArgument(JinjaKeywordArgument node) { throw expressionMisuse(node); }

    private static IllegalStateException expressionMisuse(JinjaAstNode node) {
        return new IllegalStateException("expression node " + node.nodeName() + " outside an output context");
    }

    // ------------------------------------------------------------ expressions

    /** Evaluates Jinja expressions to runtime values. */
    private final class ExpressionEvaluator implements JinjaAstVisitor<Value> {

        Value eval(JinjaExpression e) { return e.accept(this); }

        @Override public Value visitVariable(VariableReference node) {
            Value v = lookup(node.name());
            if (v == null) {
                if (node.name().equals("url_for")) return new BuiltinFunction("url_for", (a, k) -> new StringValue(urls.resolve(a.get(0).asText(), k, inFormAction, node.position())));
                if (node.name().equals("range")) return runtime.globals().get("range");
                throw new GenerationException("GEN002", "UndefinedVariable", "'" + node.name() + "' is undefined at generation time", node.position());
            }
            return v;
        }

        @Override public Value visitPropertyAccess(PropertyAccess node) {
            Value target = eval(node.target());
            return property(target, node.property(), node.position());
        }

        private Value property(Value target, String name, SourcePosition pos) {
            if (target instanceof DictValue) {
                Value v = ((DictValue) target).get(name);
                if (v != null) return v;
            }
            if (target instanceof ObjectValue) {
                Value v = ((ObjectValue) target).get(name);
                if (v != null) return v;
            }
            Value method = runtime.methodOf(target, name, pos);
            if (method != null) return method;
            if (target instanceof DictValue || target.isNone()) return NoneValue.NONE;   // Jinja: undefined attribute renders empty
            throw new GenerationException("GEN002", "UndefinedProperty", "'" + target.typeName() + "' has no property '" + name + "'", pos);
        }

        @Override public Value visitIndexAccess(IndexAccess node) {
            Value target = eval(node.target());
            Value index = eval(node.index());
            if (target instanceof DictValue) {
                Value v = ((DictValue) target).get(index.asText());
                return v == null ? NoneValue.NONE : v;
            }
            if (target instanceof ListValue) {
                List<Value> items = ((ListValue) target).items();
                int i = (int) runtime.asNumber(index, node.position()).longValue();
                if (i < 0) i += items.size();
                if (i < 0 || i >= items.size()) return NoneValue.NONE;
                return items.get(i);
            }
            if (target instanceof StringValue) {
                String s = ((StringValue) target).value();
                int i = (int) runtime.asNumber(index, node.position()).longValue();
                if (i < 0) i += s.length();
                if (i < 0 || i >= s.length()) return NoneValue.NONE;
                return new StringValue(String.valueOf(s.charAt(i)));
            }
            throw new GenerationException("GEN005", "TypeError", "'" + target.typeName() + "' is not subscriptable", node.position());
        }

        @Override public Value visitFilter(FilterExpression node) {
            Value target = eval(node.target());
            List<Value> args = new ArrayList<>();
            for (JinjaExpression a : node.arguments()) args.add(eval(a));
            Map<String, Value> kwargs = new LinkedHashMap<>();
            for (JinjaKeywordArgument k : node.keywordArguments()) kwargs.put(k.name(), eval(k.value()));
            return filters.apply(node.filterName(), target, args, kwargs, node.position());
        }

        @Override public Value visitTest(TestExpression node) {
            boolean result;
            if (node.target() instanceof VariableReference && (node.testName().equals("defined") || node.testName().equals("undefined"))) {
                boolean defined = lookup(((VariableReference) node.target()).name()) != null;
                result = node.testName().equals("defined") == defined;
            } else if (node.target() instanceof PropertyAccess && (node.testName().equals("defined") || node.testName().equals("undefined"))) {
                PropertyAccess p = (PropertyAccess) node.target();
                Value t = eval(p.target());
                boolean defined = (t instanceof DictValue && ((DictValue) t).has(p.property()))
                        || (t instanceof ObjectValue && ((ObjectValue) t).get(p.property()) != null);
                result = node.testName().equals("defined") == defined;
            } else {
                Value subject = eval(node.target());
                Value other = node.arguments().isEmpty() ? NoneValue.NONE : eval(node.arguments().get(0));
                result = filters.test(node.testName(), subject, other, node.position());
            }
            return BooleanValue.of(node.isNegated() != result);
        }

        @Override public Value visitFunctionCall(FunctionCall node) {
            List<Value> args = new ArrayList<>();
            for (JinjaExpression a : node.arguments()) args.add(eval(a));
            Map<String, Value> kwargs = new LinkedHashMap<>();
            for (JinjaKeywordArgument k : node.keywordArguments()) kwargs.put(k.name(), eval(k.value()));
            if (node.callee() instanceof VariableReference) {
                String name = ((VariableReference) node.callee()).name();
                switch (name) {
                    case "url_for":
                        if (args.isEmpty()) throw new GenerationException("GEN007", "UrlForError", "url_for() needs an endpoint", node.position());
                        return new StringValue(urls.resolve(args.get(0).asText(), kwargs, inFormAction, node.position()));
                    case "get_flashed_messages": return new ListValue(new ArrayList<>());
                    case "dict": return new DictValue(kwargs);
                    default: break;
                }
            }
            Value callee = eval(node.callee());
            return runtime.call(callee, args, kwargs, node.position());
        }

        // دالة الرابط تحول اسم نقطة النهاية والوسائط إلى رابط للصفحة المولدة المناسبة.
        @Override public Value visitUrlFor(UrlForExpression node) {
            Map<String, Value> params = new LinkedHashMap<>();
            for (JinjaKeywordArgument k : node.parameters()) params.put(k.name(), eval(k.value()));
            return new StringValue(urls.resolve(node.endpoint(), params, inFormAction, node.position()));
        }

        @Override public Value visitLiteral(JinjaLiteral node) {
            switch (node.kind()) {
                case STRING: return new StringValue(node.stringValue());
                case INTEGER: return NumberValue.ofLong((Long) node.value());
                case FLOAT: return NumberValue.ofDouble((Double) node.value());
                case BOOLEAN: return BooleanValue.of(((Boolean) node.value()).booleanValue());
                default: return NoneValue.NONE;
            }
        }

        @Override public Value visitListLiteral(JinjaListLiteral node) {
            List<Value> items = new ArrayList<>();
            for (JinjaExpression e : node.elements()) items.add(eval(e));
            return new ListValue(items);
        }

        @Override public Value visitBinary(JinjaBinaryExpression node) {
            Value l = eval(node.left());
            Value r = eval(node.right());
            if (node.operator().equals("~")) return new StringValue(l.asText() + r.asText());
            return runtime.binary(node.operator(), l, r, node.position());
        }

        @Override public Value visitComparison(JinjaComparisonExpression node) {
            Value l = eval(node.left());
            Value r = eval(node.right());
            switch (node.operator()) {
                case "==": return BooleanValue.of(l.equalsValue(r));
                case "!=": return BooleanValue.of(!l.equalsValue(r));
                case "<": return BooleanValue.of(runtime.compare(l, r, node.position()) < 0);
                case ">": return BooleanValue.of(runtime.compare(l, r, node.position()) > 0);
                case "<=": return BooleanValue.of(runtime.compare(l, r, node.position()) <= 0);
                case ">=": return BooleanValue.of(runtime.compare(l, r, node.position()) >= 0);
                case "in": return BooleanValue.of(runtime.containsValue(r, l, node.position()));
                case "not in": return BooleanValue.of(!runtime.containsValue(r, l, node.position()));
                default: throw new GenerationException("GEN005", "TypeError", "unsupported comparison " + node.operator(), node.position());
            }
        }

        @Override public Value visitLogical(JinjaLogicalExpression node) {
            Value l = eval(node.left());
            if (node.operator().equals("and")) return l.isTruthy() ? eval(node.right()) : l;
            return l.isTruthy() ? l : eval(node.right());
        }

        @Override public Value visitUnary(JinjaUnaryExpression node) {
            Value v = eval(node.operand());
            switch (node.operator()) {
                case "not": return BooleanValue.of(!v.isTruthy());
                case "-": {
                    NumberValue n = runtime.asNumber(v, node.position());
                    return n.isInteger() ? NumberValue.ofLong(-n.longValue()) : NumberValue.ofDouble(-n.doubleValue());
                }
                default: return runtime.asNumber(v, node.position());
            }
        }

        @Override public Value visitKeywordArgument(JinjaKeywordArgument node) { return eval(node.value()); }

        // structure nodes are not expressions
        @Override public Value visitProject(JinjaProject node) { throw new IllegalStateException(); }
        @Override public Value visitTemplate(JinjaTemplate node) { throw new IllegalStateException(); }
        @Override public Value visitText(TextNode node) { throw new IllegalStateException(); }
        @Override public Value visitDoctype(HtmlDoctype node) { throw new IllegalStateException(); }
        @Override public Value visitComment(HtmlComment node) { throw new IllegalStateException(); }
        @Override public Value visitElement(HtmlElement node) { throw new IllegalStateException(); }
        @Override public Value visitAttribute(HtmlAttribute node) { throw new IllegalStateException(); }
        @Override public Value visitOutput(OutputExpression node) { return eval(node.expression()); }
        @Override public Value visitFor(ForStatement node) { throw new IllegalStateException(); }
        @Override public Value visitIf(IfStatement node) { throw new IllegalStateException(); }
        @Override public Value visitIfBranch(IfBranch node) { throw new IllegalStateException(); }
    }
}
