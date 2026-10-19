package fjc.semantic;

import fjc.python.ast.Assignment;
import fjc.python.ast.AttributeAccess;
import fjc.python.ast.BinaryExpression;
import fjc.python.ast.CallExpression;
import fjc.python.ast.ComparisonExpression;
import fjc.python.ast.ConditionalBranch;
import fjc.python.ast.DictionaryEntry;
import fjc.python.ast.DictionaryLiteral;
import fjc.python.ast.ExpressionStatement;
import fjc.python.ast.ForStatement;
import fjc.python.ast.FunctionDefinition;
import fjc.python.ast.Identifier;
import fjc.python.ast.IfStatement;
import fjc.python.ast.ImportStatement;
import fjc.python.ast.ImportedName;
import fjc.python.ast.KeywordArgument;
import fjc.python.ast.ListLiteral;
import fjc.python.ast.Literal;
import fjc.python.ast.LogicalExpression;
import fjc.python.ast.Parameter;
import fjc.python.ast.PassStatement;
import fjc.python.ast.PythonAstVisitor;
import fjc.python.ast.PythonExpression;
import fjc.python.ast.PythonProgram;
import fjc.python.ast.PythonStatement;
import fjc.python.ast.RenderTemplateCall;
import fjc.python.ast.ReturnStatement;
import fjc.python.ast.RouteDefinition;
import fjc.python.ast.SubscriptAccess;
import fjc.python.ast.UnaryExpression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static shape (type) inference over the Python AST, implemented as a
 * {@link PythonAstVisitor}. It answers questions such as "what is the shape of
 * {@code products}?" (a list of dicts with keys id/name/price/...) or "what does
 * {@code find_product(product_id)} return?" (a dict, possibly None).
 */
public final class ShapeInferencer implements PythonAstVisitor<ValueShape> {

    private final Map<String, ValueShape> moduleShapes;
    private final Map<String, FunctionDefinition> functions;
    private final Map<String, Map<String, ValueShape>> parameterShapes = new HashMap<>();
    private final Map<String, ValueShape> returnCache = new HashMap<>();
    private final Set<String> inProgress = new HashSet<>();
    private Map<String, ValueShape> locals = new HashMap<>();

    public ShapeInferencer(Map<String, ValueShape> moduleShapes, Map<String, FunctionDefinition> functions) {
        this.moduleShapes = moduleShapes;
        this.functions = functions;
    }

    /** Registers the parameter shapes of a function (route converters give int/str). */
    public void setParameterShapes(String functionName, Map<String, ValueShape> shapes) {
        parameterShapes.put(functionName, new LinkedHashMap<>(shapes));
    }

    // ------------------------------------------------------------ public API

    public ValueShape infer(PythonExpression expression, Map<String, ValueShape> localShapes) {
        Map<String, ValueShape> saved = this.locals;
        this.locals = localShapes == null ? new HashMap<>() : localShapes;
        try {
            return expression.accept(this);
        } finally {
            this.locals = saved;
        }
    }

    /** Shapes of all local variables of a function after walking its body sequentially. */
    public Map<String, ValueShape> collectLocals(FunctionDefinition fn) {
        Map<String, ValueShape> result = new LinkedHashMap<>();
        Map<String, ValueShape> params = parameterShapes.get(fn.name());
        for (Parameter p : fn.parameters()) {
            ValueShape s = params != null && params.containsKey(p.name()) ? params.get(p.name()) : ValueShape.ANY;
            result.put(p.name(), s);
        }
        walkLocals(fn.body(), result);
        return result;
    }

    private void walkLocals(List<PythonStatement> body, Map<String, ValueShape> result) {
        for (PythonStatement s : body) {
            if (s instanceof Assignment) {
                Assignment a = (Assignment) s;
                if (a.targetName() != null) {
                    ValueShape shape = infer(a.value(), result);
                    if (a.isAugmented() && result.containsKey(a.targetName())) shape = result.get(a.targetName()).union(shape);
                    result.put(a.targetName(), shape);
                }
            } else if (s instanceof ForStatement) {
                ForStatement f = (ForStatement) s;
                ValueShape iterable = infer(f.iterable(), result);
                ValueShape element = iterable.isList() ? iterable.element() : iterable.isDict() ? ValueShape.STRING : ValueShape.ANY;
                if (f.targets().size() == 1) result.put(f.targets().get(0), element);
                else for (String t : f.targets()) result.put(t, ValueShape.ANY);
                walkLocals(f.body(), result);
            } else if (s instanceof IfStatement) {
                IfStatement i = (IfStatement) s;
                for (ConditionalBranch b : i.branches()) walkLocals(b.body(), result);
                walkLocals(i.elseBody(), result);
            }
        }
    }

    /** Union of the shapes of all return statements of a user function. */
    public ValueShape returnShape(String functionName) {
        if (returnCache.containsKey(functionName)) return returnCache.get(functionName);
        FunctionDefinition fn = functions.get(functionName);
        if (fn == null) return ValueShape.ANY;
        if (!inProgress.add(functionName)) return ValueShape.ANY;   // recursion guard
        try {
            Map<String, ValueShape> fnLocals = collectLocals(fn);
            ValueShape result = null;
            for (ReturnStatement r : collectReturns(fn.body())) {
                ValueShape s = r.value() == null ? ValueShape.NONE : infer(r.value(), fnLocals);
                result = result == null ? s : result.union(s);
            }
            if (result == null) result = ValueShape.NONE;
            returnCache.put(functionName, result);
            return result;
        } finally {
            inProgress.remove(functionName);
        }
    }

    private static List<ReturnStatement> collectReturns(List<PythonStatement> body) {
        List<ReturnStatement> out = new java.util.ArrayList<>();
        collectReturns(body, out);
        return out;
    }

    private static void collectReturns(List<PythonStatement> body, List<ReturnStatement> out) {
        for (PythonStatement s : body) {
            if (s instanceof ReturnStatement) out.add((ReturnStatement) s);
            else if (s instanceof ForStatement) collectReturns(((ForStatement) s).body(), out);
            else if (s instanceof IfStatement) {
                for (ConditionalBranch b : ((IfStatement) s).branches()) collectReturns(b.body(), out);
                collectReturns(((IfStatement) s).elseBody(), out);
            }
        }
    }

    // ------------------------------------------------------------- builtins

    /** Shapes of the names Flask / Python make available without definition. */
    public static Map<String, ValueShape> builtinShapes() {
        Map<String, ValueShape> m = new LinkedHashMap<>();
        Map<String, ValueShape> request = new LinkedHashMap<>();
        request.put("method", ValueShape.STRING);
        request.put("form", ValueShape.object("form", null));
        request.put("args", ValueShape.object("args", null));
        request.put("path", ValueShape.STRING);
        request.put("url", ValueShape.STRING);
        request.put("endpoint", ValueShape.STRING);
        request.put("values", ValueShape.object("values", null));
        m.put("request", ValueShape.object("request", request));
        m.put("__name__", ValueShape.STRING);
        for (String fn : List.of("print", "len", "int", "float", "str", "bool", "range", "list", "dict", "min", "max",
                "abs", "round", "sorted", "sum", "isinstance", "Flask", "render_template", "redirect", "url_for",
                "flash", "abort", "send_from_directory", "jsonify", "make_response")) {
            m.put(fn, ValueShape.function(fn));
        }
        return m;
    }

    private static final Map<String, ValueShape> BUILTIN_RETURNS = new HashMap<>();
    static {
        BUILTIN_RETURNS.put("len", ValueShape.INTEGER);
        BUILTIN_RETURNS.put("int", ValueShape.INTEGER);
        BUILTIN_RETURNS.put("float", ValueShape.FLOAT);
        BUILTIN_RETURNS.put("str", ValueShape.STRING);
        BUILTIN_RETURNS.put("bool", ValueShape.BOOLEAN);
        BUILTIN_RETURNS.put("isinstance", ValueShape.BOOLEAN);
        BUILTIN_RETURNS.put("range", ValueShape.list(ValueShape.INTEGER));
        BUILTIN_RETURNS.put("list", ValueShape.list(ValueShape.ANY));
        BUILTIN_RETURNS.put("dict", ValueShape.openDict());
        BUILTIN_RETURNS.put("url_for", ValueShape.STRING);
        BUILTIN_RETURNS.put("flash", ValueShape.NONE);
        BUILTIN_RETURNS.put("print", ValueShape.NONE);
        BUILTIN_RETURNS.put("render_template", ValueShape.object("response", null));
        BUILTIN_RETURNS.put("redirect", ValueShape.object("response", null));
        BUILTIN_RETURNS.put("send_from_directory", ValueShape.object("response", null));
        BUILTIN_RETURNS.put("jsonify", ValueShape.object("response", null));
        BUILTIN_RETURNS.put("make_response", ValueShape.object("response", null));
        BUILTIN_RETURNS.put("abort", ValueShape.NONE);
        Map<String, ValueShape> env = new LinkedHashMap<>();
        env.put("autoescape", ValueShape.BOOLEAN);
        env.put("globals", ValueShape.openDict());
        env.put("filters", ValueShape.openDict());
        env.put("trim_blocks", ValueShape.BOOLEAN);
        env.put("lstrip_blocks", ValueShape.BOOLEAN);
        Map<String, ValueShape> flask = new LinkedHashMap<>();
        flask.put("route", ValueShape.function("route"));
        flask.put("run", ValueShape.function("run"));
        flask.put("errorhandler", ValueShape.function("errorhandler"));
        flask.put("add_url_rule", ValueShape.function("add_url_rule"));
        flask.put("secret_key", ValueShape.STRING);
        flask.put("config", ValueShape.openDict());
        flask.put("jinja_env", ValueShape.object("Environment", env));
        flask.put("static_folder", ValueShape.STRING);
        flask.put("template_folder", ValueShape.STRING);
        flask.put("debug", ValueShape.BOOLEAN);
        flask.put("name", ValueShape.STRING);
        BUILTIN_RETURNS.put("Flask", ValueShape.object("Flask", flask));
    }

    // -------------------------------------------------------------- visitor

    @Override public ValueShape visitIdentifier(Identifier node) {
        String n = node.name();
        if (locals.containsKey(n)) return locals.get(n);
        if (moduleShapes.containsKey(n)) return moduleShapes.get(n);
        ValueShape b = builtinShapes().get(n);
        return b != null ? b : ValueShape.ANY;
    }

    @Override public ValueShape visitLiteral(Literal node) {
        switch (node.kind()) {
            case STRING: return ValueShape.STRING;
            case INTEGER: return ValueShape.INTEGER;
            case FLOAT: return ValueShape.FLOAT;
            case BOOLEAN: return ValueShape.BOOLEAN;
            default: return ValueShape.NONE;
        }
    }

    @Override public ValueShape visitListLiteral(ListLiteral node) {
        ValueShape element = null;
        for (PythonExpression e : node.elements()) {
            ValueShape s = e.accept(this);
            element = element == null ? s : element.union(s);
        }
        return ValueShape.list(element == null ? ValueShape.ANY : element);
    }

    @Override public ValueShape visitDictionaryLiteral(DictionaryLiteral node) {
        Map<String, ValueShape> fields = new LinkedHashMap<>();
        for (DictionaryEntry e : node.entries()) {
            if (e.keyName() == null) return ValueShape.openDict();
            fields.put(e.keyName(), e.value().accept(this));
        }
        return ValueShape.dict(fields);
    }

    @Override public ValueShape visitDictionaryEntry(DictionaryEntry node) { return node.value().accept(this); }

    @Override public ValueShape visitAttributeAccess(AttributeAccess node) {
        ValueShape target = node.target().accept(this);
        if (target.isObject() && target.hasKnownFields()) return target.field(node.attribute());
        return ValueShape.ANY;
    }

    @Override public ValueShape visitSubscriptAccess(SubscriptAccess node) {
        ValueShape target = node.target().accept(this);
        if (target.isDict()) {
            String key = node.keyName();
            return key != null && target.hasKnownFields() ? target.field(key) : ValueShape.ANY;
        }
        if (target.isList()) return target.element();
        if (target.isString()) return ValueShape.STRING;
        return ValueShape.ANY;
    }

    @Override public ValueShape visitCall(CallExpression node) {
        String name = node.calleeName();
        if (node.callee() instanceof Identifier) {
            if (functions.containsKey(name) && !locals.containsKey(name)) return returnShape(name);
            ValueShape r = BUILTIN_RETURNS.get(name);
            if (r != null) return r;
            ValueShape shape = visitIdentifier((Identifier) node.callee());
            return shape.isAny() ? ValueShape.ANY : ValueShape.ANY;
        }
        if (node.callee() instanceof AttributeAccess) {
            AttributeAccess attr = (AttributeAccess) node.callee();
            ValueShape target = attr.target().accept(this);
            String method = attr.attribute();
            switch (method) {
                case "append": case "remove": case "clear": case "sort": case "reverse": case "extend": case "insert": case "update":
                    return ValueShape.NONE;
                case "get":
                    if (target.isDict() && !node.arguments().isEmpty() && node.arguments().get(0) instanceof Literal) {
                        Literal key = (Literal) node.arguments().get(0);
                        if (key.isString() && target.hasField(key.stringValue())) return target.field(key.stringValue());
                    }
                    if (target.isObject() && (target.objectName().equals("form") || target.objectName().equals("args")
                            || target.objectName().equals("values"))) return ValueShape.STRING;
                    return ValueShape.ANY;
                case "keys": case "values": case "items": return ValueShape.list(ValueShape.ANY);
                case "strip": case "lower": case "upper": case "title": case "capitalize": case "replace": case "format": case "join":
                    return ValueShape.STRING;
                case "split": return ValueShape.list(ValueShape.STRING);
                case "startswith": case "endswith": case "isdigit": case "isalpha": case "isnumeric":
                    return ValueShape.BOOLEAN;
                case "index": case "count": return ValueShape.INTEGER;
                case "copy": return target;
                default: return ValueShape.ANY;
            }
        }
        return ValueShape.ANY;
    }

    @Override public ValueShape visitKeywordArgument(KeywordArgument node) { return node.value().accept(this); }
    @Override public ValueShape visitRenderTemplateCall(RenderTemplateCall node) { return ValueShape.object("response", null); }

    @Override public ValueShape visitBinary(BinaryExpression node) {
        ValueShape l = node.left().accept(this);
        ValueShape r = node.right().accept(this);
        if (l.isNumber() && r.isNumber()) {
            if (node.operator().equals("/")) return ValueShape.FLOAT;
            return (l.kind() == ValueShape.Kind.INTEGER && r.kind() == ValueShape.Kind.INTEGER) ? ValueShape.INTEGER : ValueShape.FLOAT;
        }
        if (node.operator().equals("+")) {
            if (l.isString() && r.isString()) return ValueShape.STRING;
            if (l.isList() && r.isList()) return l.union(r);
        }
        if (node.operator().equals("%") && l.isString()) return ValueShape.STRING;
        if (node.operator().equals("*") && (l.isString() || r.isString())) return ValueShape.STRING;
        return ValueShape.ANY;
    }

    @Override public ValueShape visitComparison(ComparisonExpression node) { return ValueShape.BOOLEAN; }
    @Override public ValueShape visitLogical(LogicalExpression node) {
        ValueShape l = node.left().accept(this);
        ValueShape r = node.right().accept(this);
        return l.union(r);
    }
    @Override public ValueShape visitUnary(UnaryExpression node) {
        return node.operator().equals("not") ? ValueShape.BOOLEAN : node.operand().accept(this);
    }

    // statements are not expressions: they have no shape
    @Override public ValueShape visitProgram(PythonProgram node) { return ValueShape.NONE; }
    @Override public ValueShape visitImport(ImportStatement node) { return ValueShape.NONE; }
    @Override public ValueShape visitAssignment(Assignment node) { return ValueShape.NONE; }
    @Override public ValueShape visitFunctionDefinition(FunctionDefinition node) { return ValueShape.function(node.name()); }
    @Override public ValueShape visitRouteDefinition(RouteDefinition node) { return ValueShape.function(node.endpoint()); }
    @Override public ValueShape visitReturn(ReturnStatement node) { return node.value() == null ? ValueShape.NONE : node.value().accept(this); }
    @Override public ValueShape visitIf(IfStatement node) { return ValueShape.NONE; }
    @Override public ValueShape visitFor(ForStatement node) { return ValueShape.NONE; }
    @Override public ValueShape visitExpressionStatement(ExpressionStatement node) { return node.expression().accept(this); }
    @Override public ValueShape visitPass(PassStatement node) { return ValueShape.NONE; }
    @Override public ValueShape visitParameter(Parameter node) { return ValueShape.ANY; }
    @Override public ValueShape visitConditionalBranch(ConditionalBranch node) { return ValueShape.NONE; }
    @Override public ValueShape visitImportedName(ImportedName node) { return ValueShape.ANY; }
}
