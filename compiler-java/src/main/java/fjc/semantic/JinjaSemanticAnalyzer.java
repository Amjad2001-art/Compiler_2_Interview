package fjc.semantic;

import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;
import fjc.common.SourcePosition;
import fjc.jinja.ast.FilterExpression;
import fjc.jinja.ast.ForStatement;
import fjc.jinja.ast.FunctionCall;
import fjc.jinja.ast.HtmlAttribute;
import fjc.jinja.ast.HtmlElement;
import fjc.jinja.ast.IfBranch;
import fjc.jinja.ast.IfStatement;
import fjc.jinja.ast.IndexAccess;
import fjc.jinja.ast.JinjaAstNode;
import fjc.jinja.ast.JinjaBinaryExpression;
import fjc.jinja.ast.JinjaComparisonExpression;
import fjc.jinja.ast.JinjaExpression;
import fjc.jinja.ast.JinjaKeywordArgument;
import fjc.jinja.ast.JinjaListLiteral;
import fjc.jinja.ast.JinjaLiteral;
import fjc.jinja.ast.JinjaLogicalExpression;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.ast.JinjaUnaryExpression;
import fjc.jinja.ast.OutputExpression;
import fjc.jinja.ast.PropertyAccess;
import fjc.jinja.ast.TestExpression;
import fjc.jinja.ast.UrlForExpression;
import fjc.jinja.ast.VariableReference;
import fjc.python.ast.RouteDefinition;
import fjc.symbols.SymbolKind;
import fjc.symbols.SymbolTable;

import java.util.ArrayDeque;
import java.util.Deque;
// import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic analysis of one Jinja template against the context the Flask
 * routes provide. Error codes:
 * <ul>
 *   <li>JJ001 UndefinedVariable – variable not provided by any route and not a loop variable/global</li>
 *   <li>JJ002 UnknownProperty – property that does not exist on the known shape (product.unknownField)</li>
 *   <li>JJ003 NonIterableLoop – for-loop over an undefined or non-iterable value</li>
 *   <li>JJ004 UnknownEndpoint – url_for() of an endpoint that no route defines</li>
 *   <li>JJ005 VariableOutOfScope – loop variable (or 'loop') used outside its loop</li>
 *   <li>JJ006 UnknownFilter</li>
 *   <li>JJ007 UrlForParameterMismatch – missing/unexpected/badly typed url_for parameters</li>
 *   <li>JJ008 UnknownTest</li>
 *   <li>JJ010 FilterArgumentMismatch – wrong number of filter arguments / wrong input type</li>
 *   <li>JJ011 StaticFileNotFound – url_for('static', filename=...) of a missing file</li>
 *   <li>JJ012 UnknownFunction – call of a function that templates do not know</li>
 *   <li>JJ013 IncompatibleComparison – comparing values of incompatible types</li>
 *   <li>JJ014 InvalidOutputType – rendering a list/dict/function directly (warning)</li>
 * </ul>
 */
// كشف أخطاء قوالب جينجا: المتغيرات والنطاقات والحلقات والمرشحات والروابط.
public final class JinjaSemanticAnalyzer {

    /** Known filters with their allowed argument counts (min, max; -1 = unlimited). */
    static final Map<String, int[]> FILTERS = new LinkedHashMap<>();
    static {
        String[] zeroArg = {"length", "count", "upper", "lower", "capitalize", "title", "trim", "string", "first", "last",
                "escape", "e", "safe", "abs", "list", "reverse", "striptags", "urlencode", "wordcount", "pprint",
                "random", "min", "max", "unique", "items", "forceescape", "float", "int"};
        for (String f : zeroArg) FILTERS.put(f, new int[]{0, 0});
        FILTERS.put("int", new int[]{0, 2});
        FILTERS.put("float", new int[]{0, 1});
        FILTERS.put("truncate", new int[]{0, 4});
        FILTERS.put("default", new int[]{0, 2});
        FILTERS.put("d", new int[]{0, 2});
        FILTERS.put("round", new int[]{0, 2});
        FILTERS.put("join", new int[]{0, 2});
        FILTERS.put("format", new int[]{0, -1});
        FILTERS.put("replace", new int[]{2, 3});
        FILTERS.put("sort", new int[]{0, 3});
        FILTERS.put("center", new int[]{0, 1});
        FILTERS.put("indent", new int[]{0, 3});
        FILTERS.put("tojson", new int[]{0, 1});
        FILTERS.put("sum", new int[]{0, 2});
        FILTERS.put("batch", new int[]{1, 2});
        FILTERS.put("slice", new int[]{1, 2});
        FILTERS.put("dictsort", new int[]{0, 3});
        FILTERS.put("filesizeformat", new int[]{0, 1});
        FILTERS.put("attr", new int[]{1, 1});
        FILTERS.put("map", new int[]{1, -1});
        FILTERS.put("select", new int[]{0, -1});
        FILTERS.put("reject", new int[]{0, -1});
        FILTERS.put("selectattr", new int[]{1, -1});
        FILTERS.put("rejectattr", new int[]{1, -1});
        FILTERS.put("groupby", new int[]{1, 2});
        FILTERS.put("xmlattr", new int[]{0, 1});
    }

    static final Set<String> TESTS = Set.of("defined", "undefined", "none", "number", "string", "iterable", "mapping",
            "sequence", "even", "odd", "divisibleby", "equalto", "eq", "ne", "lt", "gt", "le", "ge", "sameas", "boolean",
            "true", "false", "integer", "float", "lower", "upper", "callable", "in", "escaped");

    static final Set<String> STRING_METHODS = Set.of("upper", "lower", "title", "strip", "replace", "startswith",
            "endswith", "split", "capitalize", "format", "join", "lstrip", "rstrip", "zfill", "center");

    private static final ValueShape LOOP_SHAPE;
    static {
        Map<String, ValueShape> f = new LinkedHashMap<>();
        f.put("index", ValueShape.INTEGER);
        f.put("index0", ValueShape.INTEGER);
        f.put("revindex", ValueShape.INTEGER);
        f.put("revindex0", ValueShape.INTEGER);
        f.put("first", ValueShape.BOOLEAN);
        f.put("last", ValueShape.BOOLEAN);
        f.put("length", ValueShape.INTEGER);
        f.put("depth", ValueShape.INTEGER);
        f.put("depth0", ValueShape.INTEGER);
        f.put("cycle", ValueShape.function("cycle"));
        f.put("previtem", ValueShape.ANY);
        f.put("nextitem", ValueShape.ANY);
        f.put("changed", ValueShape.function("changed"));
        LOOP_SHAPE = ValueShape.object("loop", f);
    }

    private static final ValueShape REQUEST_SHAPE;
    static {
        Map<String, ValueShape> f = new LinkedHashMap<>();
        f.put("method", ValueShape.STRING);
        f.put("path", ValueShape.STRING);
        f.put("url", ValueShape.STRING);
        f.put("endpoint", ValueShape.STRING);
        f.put("args", ValueShape.object("args", null));
        f.put("form", ValueShape.object("form", null));
        REQUEST_SHAPE = ValueShape.object("request", f);
    }

    private final ErrorCollector errors;
    private final SymbolTable symbols;
    private final FlaskModel model;

    private final Deque<Map<String, ValueShape>> scopes = new ArrayDeque<>();
    private final Set<String> expiredLoopVariables = new HashSet<>();
    private final Set<String> reportedUndefined = new HashSet<>();
    private Set<String> pythonVisible = new HashSet<>();
    private String templateName;
    private boolean rendered;

    public JinjaSemanticAnalyzer(ErrorCollector errors, SymbolTable symbols, FlaskModel model) {
        this.errors = errors;
        this.symbols = symbols;
        this.model = model;
    }

    public void analyze(JinjaTemplate template) {
        templateName = template.name();
        Map<String, ValueShape> context = model.unionContext(templateName);
        List<FlaskModel.RenderCall> renders = model.rendersOf(templateName);
        rendered = !renders.isEmpty();
        expiredLoopVariables.clear();
        reportedUndefined.clear();
        pythonVisible = new HashSet<>();
        for (FlaskModel.RenderCall rc : renders) pythonVisible.addAll(model.pythonVisibleNames(rc.route()));

        symbols.enter(templateName, "template");
        Map<String, ValueShape> root = new LinkedHashMap<>();
        root.put("url_for", ValueShape.function("url_for"));
        root.put("range", ValueShape.function("range"));
        root.put("request", REQUEST_SHAPE);
        root.put("config", ValueShape.openDict());
        root.put("get_flashed_messages", ValueShape.function("get_flashed_messages"));
        for (Map.Entry<String, ValueShape> e : root.entrySet()) {
            symbols.define(e.getKey(), SymbolKind.TEMPLATE_GLOBAL, e.getValue(), template.position(), "Jinja/Flask global");
        }
        for (Map.Entry<String, ValueShape> e : context.entrySet()) {
            StringBuilder from = new StringBuilder("passed by ");
            boolean first = true;
            for (FlaskModel.RenderCall rc : renders) {
                if (rc.provides(e.getKey())) {
                    if (!first) from.append(", ");
                    first = false;
                    from.append(rc.route().endpoint()).append("()");
                }
            }
            symbols.define(e.getKey(), SymbolKind.CONTEXT_VARIABLE, e.getValue(), template.position(), from.toString());
        }
        root.putAll(context);
        scopes.push(root);
        analyzeNodes(template.children());
        scopes.pop();
        symbols.exit();
    }

    // ----------------------------------------------------------------- scopes

    private ValueShape lookup(String name) {
        for (Map<String, ValueShape> s : scopes) if (s.containsKey(name)) return s.get(name);
        return null;
    }

    private boolean isDefined(String name) {
        for (Map<String, ValueShape> s : scopes) if (s.containsKey(name)) return true;
        return false;
    }

    private void analyzeNodes(List<? extends JinjaAstNode> nodes) {
        for (JinjaAstNode n : nodes) analyzeNode(n);
    }

    private void analyzeNode(JinjaAstNode n) {
        if (n instanceof HtmlElement) {
            HtmlElement e = (HtmlElement) n;
            analyzeNodes(e.attributes());
            analyzeNodes(e.body());
        } else if (n instanceof HtmlAttribute) {
            analyzeNodes(((HtmlAttribute) n).valueParts());
        } else if (n instanceof OutputExpression) {
            ValueShape s = shapeOf(((OutputExpression) n).expression());
            if (s.isList() || s.isDict() || s.isFunction()) {
                errors.warning(ErrorCategory.JINJA, "JJ014", "InvalidOutputType",
                        "Expression renders a " + s.describe() + " directly; did you mean to loop over it or apply a filter?",
                        n.position());
            }
        } else if (n instanceof ForStatement) {
            analyzeFor((ForStatement) n);
        } else if (n instanceof IfStatement) {
            IfStatement i = (IfStatement) n;
            for (IfBranch b : i.branches()) {
                shapeOf(b.condition());
                analyzeNodes(b.body());
            }
            analyzeNodes(i.elseBody());
        }
        // TextNode, HtmlDoctype, HtmlComment: nothing to check
    }

    // متغير الحلقة لا يبقى متاحا خارج نطاقها؛ استعماله لاحقا يولد خطأ نطاق.
    private void analyzeFor(ForStatement loop) {
        ValueShape iterable;
        JinjaExpression it = loop.iterable();
        if (it instanceof VariableReference && !isDefined(((VariableReference) it).name())) {
            String name = ((VariableReference) it).name();
            if (expiredLoopVariables.contains(name)) {
                errors.error(ErrorCategory.JINJA, "JJ005", "VariableOutOfScope",
                        "Loop variable '" + name + "' is used outside the loop that defines it", it.position());
            } else if (rendered && !pythonVisible.contains(name)) {
                errors.error(ErrorCategory.JINJA, "JJ003", "NonIterableLoop",
                        "Cannot iterate over '" + name + "': it is not defined" + providedHint(), it.position());
                reportedUndefined.add(name);
            }
            iterable = ValueShape.ANY;
        } else {
            iterable = shapeOf(it);
            // الحلقة يجب أن تعمل على قيمة قابلة للتكرار؛ استعمال عدد منفرد بدلا من قائمة خطأ.
            if (!iterable.isIterable()) {
                errors.error(ErrorCategory.JINJA, "JJ003", "NonIterableLoop",
                        "Cannot iterate over a value of type " + iterable.describe() + " in '{% for "
                                + String.join(", ", loop.loopVariables()) + " in ... %}'", it.position());
            }
        }
        Map<String, ValueShape> scope = new LinkedHashMap<>();
        List<String> vars = loop.loopVariables();
        boolean itemsCall = it instanceof FunctionCall && ((FunctionCall) it).callee() instanceof PropertyAccess
                && ((PropertyAccess) ((FunctionCall) it).callee()).property().equals("items");
        if (vars.size() == 1) {
            scope.put(vars.get(0), iterable.isList() ? iterable.element() : iterable.isDict() ? ValueShape.STRING : ValueShape.ANY);
        } else if (vars.size() == 2 && itemsCall) {
            scope.put(vars.get(0), ValueShape.STRING);
            scope.put(vars.get(1), ValueShape.ANY);
        } else {
            for (String v : vars) scope.put(v, ValueShape.ANY);
        }
        scope.put("loop", LOOP_SHAPE);
        symbols.enter("for " + String.join(", ", vars), "loop");
        for (Map.Entry<String, ValueShape> e : scope.entrySet()) {
            symbols.define(e.getKey(), e.getKey().equals("loop") ? SymbolKind.TEMPLATE_GLOBAL : SymbolKind.LOOP_VARIABLE,
                    e.getValue(), loop.position(), e.getKey().equals("loop") ? "loop helper object" : "loop variable");
        }
        scopes.push(scope);
        analyzeNodes(loop.body());
        scopes.pop();
        symbols.exit();
        for (String v : vars) if (!isDefined(v)) expiredLoopVariables.add(v);
        if (!isDefined("loop")) expiredLoopVariables.add("loop");
    }

    private String providedHint() {
        if (!rendered) return " (template '" + templateName + "' is not rendered by any route)";
        Map<String, ValueShape> ctx = model.unionContext(templateName);
        return " (context provided by the routes: " + ctx.keySet() + ")";
    }

    // ------------------------------------------------------------ expressions

    /** Computes the shape of an expression, reporting semantic errors on the way. */
    ValueShape shapeOf(JinjaExpression e) {
        if (e instanceof VariableReference) return shapeOfVariable((VariableReference) e);
        if (e instanceof JinjaLiteral) {
            switch (((JinjaLiteral) e).kind()) {
                case STRING: return ValueShape.STRING;
                case INTEGER: return ValueShape.INTEGER;
                case FLOAT: return ValueShape.FLOAT;
                case BOOLEAN: return ValueShape.BOOLEAN;
                default: return ValueShape.NONE;
            }
        }
        if (e instanceof JinjaListLiteral) {
            ValueShape el = null;
            for (JinjaExpression x : ((JinjaListLiteral) e).elements()) {
                ValueShape s = shapeOf(x);
                el = el == null ? s : el.union(s);
            }
            return ValueShape.list(el);
        }
        if (e instanceof PropertyAccess) return shapeOfProperty((PropertyAccess) e, false);
        if (e instanceof IndexAccess) {
            IndexAccess ix = (IndexAccess) e;
            ValueShape target = shapeOf(ix.target());
            ValueShape index = shapeOf(ix.index());
            if (target.isDict() && ix.index() instanceof JinjaLiteral && ((JinjaLiteral) ix.index()).isString()) {
                String key = ((JinjaLiteral) ix.index()).stringValue();
                if (target.hasKnownFields() && !target.hasField(key)) {
                    errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                            "Key '" + key + "' does not exist on " + describeTarget(ix.target()) + " (known keys: "
                                    + target.fields().keySet() + ")", ix.position());
                    return ValueShape.ANY;
                }
                return target.field(key);
            }
            if (target.isList()) {
                if (index.isString()) {
                    errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                            "A list cannot be indexed with a string", ix.position());
                }
                return target.element();
            }
            return ValueShape.ANY;
        }
        if (e instanceof FilterExpression) return shapeOfFilter((FilterExpression) e);
        if (e instanceof TestExpression) {
            TestExpression t = (TestExpression) e;
            if (t.target() instanceof VariableReference && (t.testName().equals("defined") || t.testName().equals("undefined"))) {
                // "x is defined" is the idiomatic way to probe an optional variable: do not report JJ001 here
            } else {
                shapeOf(t.target());
            }
            for (JinjaExpression a : t.arguments()) shapeOf(a);
            if (!TESTS.contains(t.testName())) {
                errors.error(ErrorCategory.JINJA, "JJ008", "UnknownTest",
                        "Unknown test 'is " + t.testName() + "' (known tests: " + String.join(", ", new java.util.TreeSet<>(TESTS)) + ")",
                        t.position());
            }
            return ValueShape.BOOLEAN;
        }
        if (e instanceof UrlForExpression) return shapeOfUrlFor((UrlForExpression) e);
        if (e instanceof FunctionCall) return shapeOfCall((FunctionCall) e);
        if (e instanceof JinjaBinaryExpression) {
            JinjaBinaryExpression b = (JinjaBinaryExpression) e;
            ValueShape l = shapeOf(b.left());
            ValueShape r = shapeOf(b.right());
            if (b.operator().equals("~")) return ValueShape.STRING;
            if (l.isNumber() && r.isNumber()) {
                return (l.kind() == ValueShape.Kind.INTEGER && r.kind() == ValueShape.Kind.INTEGER && !b.operator().equals("/"))
                        ? ValueShape.INTEGER : ValueShape.FLOAT;
            }
            if (b.operator().equals("+") && l.isString() && r.isString()) return ValueShape.STRING;
            if ((l.isString() && r.isNumber()) || (l.isNumber() && r.isString())) {
                if (!b.operator().equals("*") && !b.operator().equals("%")) {
                    errors.error(ErrorCategory.JINJA, "JJ013", "IncompatibleComparison",
                            "Operator '" + b.operator() + "' cannot combine " + l.describe() + " and " + r.describe()
                                    + " (use '~' to concatenate as text)", b.position());
                }
            }
            return ValueShape.ANY;
        }
        if (e instanceof JinjaComparisonExpression) {
            JinjaComparisonExpression c = (JinjaComparisonExpression) e;
            ValueShape l = shapeOf(c.left());
            ValueShape r = shapeOf(c.right());
            checkComparison(c, l, r);
            return ValueShape.BOOLEAN;
        }
        if (e instanceof JinjaLogicalExpression) {
            JinjaLogicalExpression l = (JinjaLogicalExpression) e;
            return shapeOf(l.left()).union(shapeOf(l.right()));
        }
        if (e instanceof JinjaUnaryExpression) {
            JinjaUnaryExpression u = (JinjaUnaryExpression) e;
            ValueShape s = shapeOf(u.operand());
            return u.operator().equals("not") ? ValueShape.BOOLEAN : s;
        }
        return ValueShape.ANY;
    }

    private void checkComparison(JinjaComparisonExpression c, ValueShape l, ValueShape r) {
        String op = c.operator();
        if (op.equals("in") || op.equals("not in")) {
            if (!r.isIterable() && !r.isString()) {
                errors.error(ErrorCategory.JINJA, "JJ013", "IncompatibleComparison",
                        "'" + op + "' requires an iterable on the right-hand side, found " + r.describe(), c.position());
            }
            return;
        }
        boolean known = !l.isAny() && !r.isAny() && !l.isNone() && !r.isNone() && !l.isOptional() && !r.isOptional();
        if (!known) return;
        boolean compatible = (l.isNumber() && r.isNumber()) || (l.isNumber() && r.isBoolean()) || (l.isBoolean() && r.isNumber())
                || l.kind() == r.kind();
        if (!compatible) {
            errors.error(ErrorCategory.JINJA, "JJ013", "IncompatibleComparison",
                    "Comparison '" + op + "' between " + l.describe() + " and " + r.describe() + " can never be meaningful",
                    c.position());
        }
    }

    private ValueShape shapeOfVariable(VariableReference v) {
        String name = v.name();
        ValueShape s = lookup(name);
        if (s != null) return s;
        if (reportedUndefined.contains(name)) return ValueShape.ANY;
        reportedUndefined.add(name);
        if (expiredLoopVariables.contains(name)) {
            errors.error(ErrorCategory.JINJA, "JJ005", "VariableOutOfScope",
                    (name.equals("loop") ? "'loop' is only available inside a '{% for %}' block"
                            : "Loop variable '" + name + "' is used outside the loop that defines it"), v.position());
            return ValueShape.ANY;
        }
        if (name.equals("loop")) {
            errors.error(ErrorCategory.JINJA, "JJ005", "VariableOutOfScope",
                    "'loop' is only available inside a '{% for %}' block", v.position());
            return ValueShape.ANY;
        }
        if (rendered && pythonVisible.contains(name)) {
            // A Python variable with this name exists but is not passed by the route:
            // reported on the Python side as PY004 by the cross-language analyser.
            return ValueShape.ANY;
        }
        if (!rendered) {
            // No route renders this template (XL003 warning): its variables cannot be checked.
            return ValueShape.ANY;
        }
        // متغير القالب يجب أن يأتي من السياق أو من نطاق محلي معروف؛ وإلا يُبلغ عن غيابه.
        errors.error(ErrorCategory.JINJA, "JJ001", "UndefinedVariable",
                "Variable '" + name + "' is not defined" + providedHint(), v.position());
        return ValueShape.ANY;
    }

    private String describeTarget(JinjaExpression target) {
        if (target instanceof VariableReference) return "'" + ((VariableReference) target).name() + "'";
        if (target instanceof PropertyAccess && ((PropertyAccess) target).dottedName() != null) return "'" + ((PropertyAccess) target).dottedName() + "'";
        return "the value";
    }

    private ValueShape shapeOfProperty(PropertyAccess p, boolean asMethod) {
        ValueShape target = shapeOf(p.target());
        String prop = p.property();
        if (target.isAny()) return ValueShape.ANY;
        if ((target.isDict() || target.isObject()) && target.hasKnownFields()) {
            if (!target.hasField(prop)) {
                if (target.isDict() && asMethod && Set.of("get", "items", "keys", "values").contains(prop)) return ValueShape.function(prop);
                errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                        "Property '" + prop + "' does not exist on " + describeTarget(p.target()) + " (known properties: "
                                + target.fields().keySet() + ")", p.position());
                return ValueShape.ANY;
            }
            return target.field(prop);
        }
        if (target.isDict() || target.isObject()) return ValueShape.ANY;   // open dict / object
        if (target.isString()) {
            if (asMethod && STRING_METHODS.contains(prop)) return ValueShape.function(prop);
            errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                    "Property '" + prop + "' does not exist on " + describeTarget(p.target()) + " (a string)", p.position());
            return ValueShape.ANY;
        }
        if (target.isList()) {
            errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                    "Property '" + prop + "' does not exist on " + describeTarget(p.target()) + " (a list; use |length or index it)",
                    p.position());
            return ValueShape.ANY;
        }
        if (target.isNumber() || target.isBoolean() || target.isNone()) {
            errors.error(ErrorCategory.JINJA, "JJ002", "UnknownProperty",
                    "Property '" + prop + "' does not exist on " + describeTarget(p.target()) + " (a " + target.describe() + ")",
                    p.position());
            return ValueShape.ANY;
        }
        return ValueShape.ANY;
    }

    private ValueShape shapeOfFilter(FilterExpression f) {
        ValueShape target = shapeOf(f.target());
        for (JinjaExpression a : f.arguments()) shapeOf(a);
        for (JinjaKeywordArgument k : f.keywordArguments()) shapeOf(k.value());
        int[] arity = FILTERS.get(f.filterName());
        if (arity == null) {
            // يُفحص اسم المرشح مقابل المرشحات المدعومة؛ الاسم غير المعروف ينتج تشخيصا.
            errors.error(ErrorCategory.JINJA, "JJ006", "UnknownFilter",
                    "Unknown filter '|" + f.filterName() + "' (known filters: " + String.join(", ", new java.util.TreeSet<>(FILTERS.keySet())) + ")",
                    f.position());
            return ValueShape.ANY;
        }
        int given = f.arguments().size() + f.keywordArguments().size();
        if (given < arity[0] || (arity[1] >= 0 && given > arity[1])) {
            errors.error(ErrorCategory.JINJA, "JJ010", "FilterArgumentMismatch",
                    "Filter '|" + f.filterName() + "' takes " + (arity[1] < 0 ? "at least " + arity[0]
                            : arity[0] == arity[1] ? arity[0] : arity[0] + " to " + arity[1]) + " argument(s) but " + given + " were given",
                    f.position());
        }
        switch (f.filterName()) {
            case "length": case "count": case "wordcount":
                if (target.isNumber() || target.isBoolean()) {
                    errors.error(ErrorCategory.JINJA, "JJ010", "FilterArgumentMismatch",
                            "Filter '|" + f.filterName() + "' cannot be applied to a " + target.describe(), f.position());
                }
                return ValueShape.INTEGER;
            case "format":
                if (!target.isString() && !target.isAny()) {
                    errors.error(ErrorCategory.JINJA, "JJ010", "FilterArgumentMismatch",
                            "Filter '|format' must be applied to a format string, found " + target.describe(), f.position());
                }
                return ValueShape.STRING;
            case "upper": case "lower": case "capitalize": case "title": case "trim": case "string": case "escape": case "e":
            case "safe": case "join": case "replace": case "striptags": case "urlencode": case "center": case "indent":
            case "tojson": case "filesizeformat": case "truncate": case "forceescape": case "xmlattr":
                return ValueShape.STRING;
            case "round": return ValueShape.FLOAT;
            case "int": return ValueShape.INTEGER;
            case "float": return ValueShape.FLOAT;
            case "abs": return target.isNumber() ? target : ValueShape.FLOAT;
            case "default": case "d":
                return f.arguments().isEmpty() ? target : target.union(shapeOf(f.arguments().get(0)));
            case "list": case "reverse": case "sort": case "unique": case "batch": case "slice": case "map": case "select":
            case "reject": case "selectattr": case "rejectattr":
                return target.isList() ? target : ValueShape.list(ValueShape.ANY);
            case "first": case "last": case "random": case "min": case "max":
                return target.isList() ? target.element() : ValueShape.ANY;
            case "items": case "dictsort": return ValueShape.list(ValueShape.ANY);
            case "sum": return ValueShape.FLOAT;
            default: return ValueShape.ANY;
        }
    }

    private ValueShape shapeOfUrlFor(UrlForExpression u) {
        for (JinjaKeywordArgument k : u.parameters()) shapeOf(k.value());
        String endpoint = u.endpoint();
        if (endpoint.equals("static")) {
            JinjaKeywordArgument filename = u.parameter("filename");
            if (filename == null) {
                errors.error(ErrorCategory.JINJA, "JJ007", "UrlForParameterMismatch",
                        "url_for('static') requires a filename= parameter", u.position());
            } else if (filename.value() instanceof JinjaLiteral && ((JinjaLiteral) filename.value()).isString()) {
                String name = ((JinjaLiteral) filename.value()).stringValue();
                if (!model.hasStaticFile(name)) {
                    errors.error(ErrorCategory.JINJA, "JJ011", "StaticFileNotFound",
                            "Static file '" + name + "' does not exist in static/ (available: " + model.staticFiles() + ")",
                            filename.position());
                }
            }
            return ValueShape.STRING;
        }
        FlaskModel.RouteInfo route = model.route(endpoint);
        if (route == null) {
            List<String> known = new java.util.ArrayList<>();
            // يُقارن اسم المسار داخل دالة الرابط مع المسارات المعروفة في نموذج Flask.
            for (FlaskModel.RouteInfo r : model.routes()) known.add(r.endpoint());
            errors.error(ErrorCategory.JINJA, "JJ004", "UnknownEndpoint",
                    "url_for() refers to endpoint '" + endpoint + "' but no Flask route defines it (known endpoints: "
                            + known + ")", u.endpointPosition());
            return ValueShape.STRING;
        }
        Set<String> expected = new HashSet<>(route.parameterNames());
        Set<String> given = new HashSet<>(u.parameterNames());
        for (String p : expected) {
            if (!given.contains(p)) {
                errors.error(ErrorCategory.JINJA, "JJ007", "UrlForParameterMismatch",
                        "url_for('" + endpoint + "') is missing the URL parameter '" + p + "' required by route '"
                                + route.rule() + "'", u.position());
            }
        }
        for (JinjaKeywordArgument k : u.parameters()) {
            if (!expected.contains(k.name())) {
                errors.error(ErrorCategory.JINJA, "JJ007", "UrlForParameterMismatch",
                        "url_for('" + endpoint + "') passes unknown parameter '" + k.name() + "' (route '" + route.rule()
                                + "' expects " + route.parameterNames() + ")", k.position());
                continue;
            }
            ValueShape given_shape = shapeOf(k.value());
            for (RouteDefinition.RouteParameter rp : route.parameters()) {
                if (rp.name().equals(k.name()) && rp.converter().equals("int") && !given_shape.isAny()
                        && !given_shape.isNumber()) {
                    errors.error(ErrorCategory.JINJA, "JJ007", "UrlForParameterMismatch",
                            "url_for('" + endpoint + "') parameter '" + k.name() + "' must be an integer (route uses <int:"
                                    + k.name() + ">) but a " + given_shape.describe() + " is passed", k.position());
                }
            }
        }
        return ValueShape.STRING;
    }

    private ValueShape shapeOfCall(FunctionCall c) {
        for (JinjaExpression a : c.arguments()) shapeOf(a);
        for (JinjaKeywordArgument k : c.keywordArguments()) shapeOf(k.value());
        if (c.callee() instanceof VariableReference) {
            String name = ((VariableReference) c.callee()).name();
            if (name.equals("url_for")) {
                errors.error(ErrorCategory.JINJA, "JJ004", "UnknownEndpoint",
                        "url_for() requires a string literal endpoint name as its first argument", c.position());
                return ValueShape.STRING;
            }
            if (name.equals("range")) return ValueShape.list(ValueShape.INTEGER);
            if (name.equals("get_flashed_messages")) return ValueShape.list(ValueShape.STRING);
            if (name.equals("dict")) return ValueShape.openDict();
            ValueShape s = lookup(name);
            if (s == null) {
                errors.error(ErrorCategory.JINJA, "JJ012", "UnknownFunction",
                        "Function '" + name + "()' is not available in templates", c.position());
                reportedUndefined.add(name);
                return ValueShape.ANY;
            }
            if (!s.isFunction() && !s.isAny()) {
                errors.error(ErrorCategory.JINJA, "JJ012", "UnknownFunction",
                        "'" + name + "' is a " + s.describe() + ", not a function", c.position());
            }
            return ValueShape.ANY;
        }
        if (c.callee() instanceof PropertyAccess) {
            PropertyAccess p = (PropertyAccess) c.callee();
            ValueShape fn = shapeOfProperty(p, true);
            if (fn.isFunction()) {
                switch (fn.objectName()) {
                    case "upper": case "lower": case "title": case "strip": case "replace": case "capitalize": case "format":
                    case "join": case "lstrip": case "rstrip": case "zfill": case "center": return ValueShape.STRING;
                    case "startswith": case "endswith": return ValueShape.BOOLEAN;
                    case "split": return ValueShape.list(ValueShape.STRING);
                    case "items": case "keys": case "values": return ValueShape.list(ValueShape.ANY);
                    case "cycle": return ValueShape.ANY;
                    default: return ValueShape.ANY;
                }
            }
            return ValueShape.ANY;
        }
        shapeOf(c.callee());
        return ValueShape.ANY;
    }

    /** Positions of the first use of each undefined name (for tests and reports). */
    public SourcePosition positionOf(JinjaAstNode n) { return n.position(); }
}
