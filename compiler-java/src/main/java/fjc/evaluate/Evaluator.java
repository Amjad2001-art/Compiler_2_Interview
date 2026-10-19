package fjc.evaluate;

import fjc.common.SourcePosition;
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
import fjc.semantic.FlaskModel;

import java.util.ArrayList;
// import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compile-time interpreter for the supported Python subset, implemented as a
 * {@link PythonAstVisitor}. It executes the module (building the product data)
 * and the GET path of each route so that every {@code render_template} call
 * yields concrete context values for the code generator.
 *
 * <p>Flask names (Flask, render_template, redirect, url_for, request, ...) are
 * modelled by small Java builtins; {@code request.method} is always "GET"
 * because generated pages are the GET views of the application.
 */
public final class Evaluator implements PythonAstVisitor<Value> {

    private static final long MAX_STEPS = 500_000;

    /** Thrown by {@code return}. */
    static final class ReturnSignal extends RuntimeException {
        final Value value;
        ReturnSignal(Value value) { super(null, null, false, false); this.value = value; }
    }

    /** Thrown by {@code abort(code)}: the route instance produces no page. */
    public static final class AbortSignal extends RuntimeException {
        private final int code;
        public AbortSignal(int code) { super("abort(" + code + ")", null, false, false); this.code = code; }
        public int code() { return code; }
    }

    private final FlaskModel model;
    private final Environment globals = new Environment(null);
    private Environment env = globals;
    private final List<RenderInvocation> renders = new ArrayList<>();
    private long steps = 0;
    private SourcePosition lastPosition = SourcePosition.unknown("<eval>");

    public Evaluator(FlaskModel model) {
        this.model = model;
        installBuiltins();
    }

    public Environment globals() { return globals; }
    public List<RenderInvocation> renders() { return renders; }

    // --------------------------------------------------------------- driving

    public void runModule(PythonProgram program) {
        program.accept(this);
    }

    /** Calls a route function with the given URL parameters and returns the render_template calls it reached. */
    public List<RenderInvocation> callRoute(FunctionValue fn, Map<String, Value> parameters) {
        renders.clear();
        Map<String, Value> kwargs = new LinkedHashMap<>(parameters);
        call(fn, new ArrayList<>(), kwargs, fn.definition().position());
        return new ArrayList<>(renders);
    }

    // -------------------------------------------------------------- builtins

    private void installBuiltins() {
        globals.define("__name__", new StringValue("app"));
        globals.define("print", builtin("print", (a, k) -> NoneValue.NONE));
        globals.define("len", builtin("len", (a, k) -> {
            Value v = arg(a, 0, "len");
            if (v instanceof ListValue) return NumberValue.ofLong(((ListValue) v).size());
            if (v instanceof DictValue) return NumberValue.ofLong(((DictValue) v).entries().size());
            if (v instanceof StringValue) return NumberValue.ofLong(((StringValue) v).value().length());
            throw fail("EVAL004", "TypeError", "object of type '" + v.typeName() + "' has no len()");
        }));
        globals.define("int", builtin("int", (a, k) -> {
            if (a.isEmpty()) return NumberValue.ofLong(0);
            Value v = a.get(0);
            if (v instanceof NumberValue) return NumberValue.ofLong(((NumberValue) v).longValue());
            if (v instanceof BooleanValue) return NumberValue.ofLong(v.isTruthy() ? 1 : 0);
            if (v instanceof StringValue) {
                try { return NumberValue.ofLong(Long.parseLong(((StringValue) v).value().trim())); }
                catch (NumberFormatException e) { throw fail("EVAL004", "ValueError", "invalid literal for int(): " + v.repr()); }
            }
            throw fail("EVAL004", "TypeError", "int() argument must be a string or a number, not '" + v.typeName() + "'");
        }));
        globals.define("float", builtin("float", (a, k) -> {
            if (a.isEmpty()) return NumberValue.ofDouble(0);
            Value v = a.get(0);
            if (v instanceof NumberValue) return NumberValue.ofDouble(((NumberValue) v).doubleValue());
            if (v instanceof StringValue) {
                try { return NumberValue.ofDouble(Double.parseDouble(((StringValue) v).value().trim())); }
                catch (NumberFormatException e) { throw fail("EVAL004", "ValueError", "could not convert string to float: " + v.repr()); }
            }
            throw fail("EVAL004", "TypeError", "float() argument must be a string or a number, not '" + v.typeName() + "'");
        }));
        globals.define("str", builtin("str", (a, k) -> new StringValue(a.isEmpty() ? "" : a.get(0).asText())));
        globals.define("bool", builtin("bool", (a, k) -> BooleanValue.of(!a.isEmpty() && a.get(0).isTruthy())));
        globals.define("abs", builtin("abs", (a, k) -> {
            NumberValue n = number(arg(a, 0, "abs"), "abs");
            return n.isInteger() ? NumberValue.ofLong(Math.abs(n.longValue())) : NumberValue.ofDouble(Math.abs(n.doubleValue()));
        }));
        globals.define("round", builtin("round", (a, k) -> {
            NumberValue n = number(arg(a, 0, "round"), "round");
            if (a.size() < 2) return NumberValue.ofLong(Math.round(n.doubleValue()));
            int digits = (int) number(a.get(1), "round").longValue();
            double factor = Math.pow(10, digits);
            return NumberValue.ofDouble(Math.round(n.doubleValue() * factor) / factor);
        }));
        globals.define("range", builtin("range", (a, k) -> {
            long start = 0, stop, step = 1;
            if (a.size() == 1) stop = number(a.get(0), "range").longValue();
            else { start = number(a.get(0), "range").longValue(); stop = number(a.get(1), "range").longValue(); }
            if (a.size() > 2) step = number(a.get(2), "range").longValue();
            if (step == 0) throw fail("EVAL004", "ValueError", "range() step must not be zero");
            List<Value> items = new ArrayList<>();
            for (long i = start; step > 0 ? i < stop : i > stop; i += step) items.add(NumberValue.ofLong(i));
            return new ListValue(items);
        }));
        globals.define("list", builtin("list", (a, k) -> {
            if (a.isEmpty()) return new ListValue(new ArrayList<>());
            return new ListValue(iterate(a.get(0)));
        }));
        globals.define("dict", builtin("dict", (a, k) -> {
            DictValue d = a.isEmpty() ? new DictValue() : new DictValue(((DictValue) a.get(0)).entries());
            for (Map.Entry<String, Value> e : k.entrySet()) d.put(e.getKey(), e.getValue());
            return d;
        }));
        globals.define("sorted", builtin("sorted", (a, k) -> {
            List<Value> items = new ArrayList<>(iterate(arg(a, 0, "sorted")));
            items.sort(this::compareValues);
            if (k.containsKey("reverse") && k.get("reverse").isTruthy()) java.util.Collections.reverse(items);
            return new ListValue(items);
        }));
        globals.define("min", builtin("min", (a, k) -> extreme(a, -1)));
        globals.define("max", builtin("max", (a, k) -> extreme(a, 1)));
        globals.define("sum", builtin("sum", (a, k) -> {
            double total = 0;
            boolean allInt = true;
            for (Value v : iterate(arg(a, 0, "sum"))) {
                NumberValue n = number(v, "sum");
                total += n.doubleValue();
                allInt &= n.isInteger();
            }
            return allInt ? NumberValue.ofLong((long) total) : NumberValue.ofDouble(total);
        }));
        globals.define("isinstance", builtin("isinstance", (a, k) -> {
            Value v = arg(a, 0, "isinstance");
            Value type = arg(a, 1, "isinstance");
            if (!(type instanceof BuiltinFunction)) return BooleanValue.FALSE;
            String t = ((BuiltinFunction) type).name();
            switch (t) {
                case "int": return BooleanValue.of(v instanceof NumberValue && ((NumberValue) v).isInteger() || v instanceof BooleanValue);
                case "float": return BooleanValue.of(v instanceof NumberValue && !((NumberValue) v).isInteger());
                case "str": return BooleanValue.of(v instanceof StringValue);
                case "list": return BooleanValue.of(v instanceof ListValue);
                case "dict": return BooleanValue.of(v instanceof DictValue);
                case "bool": return BooleanValue.of(v instanceof BooleanValue);
                default: return BooleanValue.FALSE;
            }
        }));
        // Flask
        globals.define("Flask", builtin("Flask", (a, k) -> {
            ObjectValue app = new ObjectValue("Flask");
            app.set("route", builtin("route", (ra, rk) -> builtin("route_decorator", (da, dk) -> arg(da, 0, "route"))));
            app.set("errorhandler", builtin("errorhandler", (ra, rk) -> builtin("errorhandler_decorator", (da, dk) -> arg(da, 0, "errorhandler"))));
            app.set("run", builtin("run", (ra, rk) -> NoneValue.NONE));
            app.set("add_url_rule", builtin("add_url_rule", (ra, rk) -> NoneValue.NONE));
            app.set("config", new DictValue());
            app.set("secret_key", NoneValue.NONE);
            app.set("debug", BooleanValue.FALSE);
            app.set("name", a.isEmpty() ? new StringValue("app") : a.get(0));
            app.set("static_folder", k.getOrDefault("static_folder", new StringValue("static")));
            app.set("template_folder", k.getOrDefault("template_folder", new StringValue("templates")));
            ObjectValue jinjaEnv = new ObjectValue("Environment");
            jinjaEnv.set("autoescape", BooleanValue.TRUE);
            jinjaEnv.set("globals", new DictValue());
            jinjaEnv.set("filters", new DictValue());
            jinjaEnv.set("trim_blocks", BooleanValue.FALSE);
            jinjaEnv.set("lstrip_blocks", BooleanValue.FALSE);
            app.set("jinja_env", jinjaEnv);
            return app;
        }));
        globals.define("render_template", builtin("render_template", (a, k) -> {
            String name = arg(a, 0, "render_template").asText();
            renders.add(new RenderInvocation(name, k));
            return new ResponseValue(ResponseValue.Kind.RENDER, name);
        }));
        globals.define("redirect", builtin("redirect", (a, k) -> new ResponseValue(ResponseValue.Kind.REDIRECT, arg(a, 0, "redirect").asText())));
        globals.define("url_for", builtin("url_for", (a, k) -> new StringValue(runtimeUrl(arg(a, 0, "url_for").asText(), k))));
        globals.define("flash", builtin("flash", (a, k) -> NoneValue.NONE));
        globals.define("abort", builtin("abort", (a, k) -> { throw new AbortSignal((int) number(arg(a, 0, "abort"), "abort").longValue()); }));
        globals.define("send_from_directory", builtin("send_from_directory", (a, k) -> new ResponseValue(ResponseValue.Kind.OTHER, "file")));
        globals.define("jsonify", builtin("jsonify", (a, k) -> new ResponseValue(ResponseValue.Kind.OTHER, "json")));
        globals.define("make_response", builtin("make_response", (a, k) -> new ResponseValue(ResponseValue.Kind.OTHER, "response")));
        ObjectValue request = new ObjectValue("request");
        request.set("method", new StringValue("GET"));
        request.set("form", new DictValue());
        request.set("args", new DictValue());
        request.set("values", new DictValue());
        request.set("path", new StringValue("/"));
        request.set("url", new StringValue("/"));
        request.set("endpoint", new StringValue(""));
        globals.define("request", request);
    }

    private String runtimeUrl(String endpoint, Map<String, Value> params) {
        if (endpoint.equals("static")) {
            Value f = params.get("filename");
            return "/static/" + (f == null ? "" : f.asText());
        }
        FlaskModel.RouteInfo route = model == null ? null : model.route(endpoint);
        if (route == null) return "/" + endpoint;
        String url = route.rule();
        for (Map.Entry<String, Value> e : params.entrySet()) {
            url = url.replaceAll("<(?:[a-z]+:)?" + e.getKey() + ">", e.getValue().asText());
        }
        return url;
    }

    private static BuiltinFunction builtin(String name, BuiltinFunction.Body body) { return new BuiltinFunction(name, body); }

    private Value arg(List<Value> args, int i, String fn) {
        if (i >= args.size()) throw fail("EVAL004", "TypeError", fn + "() missing required argument #" + (i + 1));
        return args.get(i);
    }

    private NumberValue number(Value v, String where) {
        if (v instanceof NumberValue) return (NumberValue) v;
        if (v instanceof BooleanValue) return NumberValue.ofLong(v.isTruthy() ? 1 : 0);
        throw fail("EVAL004", "TypeError", where + ": expected a number, found " + v.typeName());
    }

    private Value extreme(List<Value> a, int sign) {
        List<Value> items = a.size() == 1 ? iterate(a.get(0)) : a;
        if (items.isEmpty()) throw fail("EVAL004", "ValueError", "min()/max() of an empty sequence");
        Value best = items.get(0);
        for (Value v : items) if (compareValues(v, best) * sign > 0) best = v;
        return best;
    }

    private List<Value> iterate(Value v) {
        if (v instanceof ListValue) return ((ListValue) v).items();
        if (v instanceof DictValue) {
            List<Value> keys = new ArrayList<>();
            for (String k : ((DictValue) v).entries().keySet()) keys.add(new StringValue(k));
            return keys;
        }
        if (v instanceof StringValue) {
            List<Value> chars = new ArrayList<>();
            for (char c : ((StringValue) v).value().toCharArray()) chars.add(new StringValue(String.valueOf(c)));
            return chars;
        }
        throw fail("EVAL004", "TypeError", "'" + v.typeName() + "' object is not iterable");
    }

    private int compareValues(Value a, Value b) {
        if ((a instanceof NumberValue || a instanceof BooleanValue) && (b instanceof NumberValue || b instanceof BooleanValue)) {
            return Double.compare(number(a, "compare").doubleValue(), number(b, "compare").doubleValue());
        }
        if (a instanceof StringValue && b instanceof StringValue) {
            return ((StringValue) a).value().compareTo(((StringValue) b).value());
        }
        throw fail("EVAL004", "TypeError", "'<' not supported between '" + a.typeName() + "' and '" + b.typeName() + "'");
    }

    private EvaluationException fail(String code, String name, String message) {
        return new EvaluationException(code, name, message, lastPosition);
    }

    private void step(SourcePosition pos) {
        lastPosition = pos;
        if (++steps > MAX_STEPS) throw fail("EVAL006", "ExecutionLimit", "Evaluation exceeded " + MAX_STEPS + " steps (infinite loop?)");
    }

    // ------------------------------------------------------------------ calls

    public Value call(Value callee, List<Value> args, Map<String, Value> kwargs, SourcePosition pos) {
        lastPosition = pos;
        if (callee instanceof BuiltinFunction) return ((BuiltinFunction) callee).call(args, kwargs);
        if (callee instanceof ExternalValue) return NoneValue.NONE;
        if (!(callee instanceof FunctionValue)) {
            throw fail("EVAL005", "TypeError", "'" + callee.typeName() + "' object is not callable");
        }
        FunctionValue fn = (FunctionValue) callee;
        FunctionDefinition def = fn.definition();
        Environment frame = new Environment(fn.closure());
        List<Parameter> params = def.parameters();
        for (int i = 0; i < params.size(); i++) {
            Parameter p = params.get(i);
            Value v;
            if (i < args.size()) v = args.get(i);
            else if (kwargs.containsKey(p.name())) v = kwargs.get(p.name());
            else if (p.defaultValue() != null) v = evaluateIn(p.defaultValue(), fn.closure());
            else throw fail("EVAL004", "TypeError", def.name() + "() missing required argument '" + p.name() + "'");
            frame.define(p.name(), v);
        }
        if (args.size() > params.size()) {
            throw fail("EVAL004", "TypeError", def.name() + "() takes " + params.size() + " arguments but " + args.size() + " were given");
        }
        Environment saved = env;
        env = frame;
        try {
            executeBlock(def.body());
            return NoneValue.NONE;
        } catch (ReturnSignal r) {
            return r.value;
        } finally {
            env = saved;
        }
    }

    private Value evaluateIn(PythonExpression e, Environment scope) {
        Environment saved = env;
        env = scope;
        try { return e.accept(this); } finally { env = saved; }
    }

    private void executeBlock(List<PythonStatement> body) {
        for (PythonStatement s : body) {
            step(s.position());
            s.accept(this);
        }
    }

    // ------------------------------------------------------------ statements

    @Override public Value visitProgram(PythonProgram node) {
        executeBlock(node.statements());
        return NoneValue.NONE;
    }

    @Override public Value visitImport(ImportStatement node) {
        for (ImportedName n : node.names()) {
            String bound = n.boundName();
            Value known = node.isFromImport() ? globals.get(n.name()) : null;
            if (known != null && (node.module().equals("flask") || known instanceof BuiltinFunction || known instanceof ObjectValue)) {
                env.define(bound, known);
            } else {
                env.define(bound, new ExternalValue(node.module() + "." + n.name()));
            }
        }
        return NoneValue.NONE;
    }

    @Override public Value visitAssignment(Assignment node) {
        Value value = node.value().accept(this);
        PythonExpression target = node.target();
        if (node.isAugmented()) {
            Value current = target.accept(this);
            value = arithmetic(node.operator().substring(0, 1), current, value, node.position());
        }
        if (target instanceof Identifier) {
            // Python scoping: an assignment binds the name in the current frame (module or function)
            env.define(((Identifier) target).name(), value);
        } else if (target instanceof AttributeAccess) {
            AttributeAccess a = (AttributeAccess) target;
            Value obj = a.target().accept(this);
            if (obj instanceof ObjectValue) ((ObjectValue) obj).set(a.attribute(), value);
            else if (!(obj instanceof ExternalValue)) throw fail("EVAL002", "AttributeError", "cannot set attribute '" + a.attribute() + "' on " + obj.typeName());
        } else if (target instanceof SubscriptAccess) {
            SubscriptAccess s = (SubscriptAccess) target;
            Value obj = s.target().accept(this);
            Value index = s.index().accept(this);
            if (obj instanceof DictValue) ((DictValue) obj).put(index.asText(), value);
            else if (obj instanceof ListValue) {
                List<Value> items = ((ListValue) obj).items();
                int i = listIndex(items.size(), index);
                items.set(i, value);
            } else throw fail("EVAL003", "TypeError", "'" + obj.typeName() + "' object does not support item assignment");
        }
        return NoneValue.NONE;
    }

    @Override public Value visitFunctionDefinition(FunctionDefinition node) {
        Value fn = new FunctionValue(node, env);
        for (int i = node.decorators().size() - 1; i >= 0; i--) {
            Value decorator = node.decorators().get(i).accept(this);
            List<Value> a = new ArrayList<>();
            a.add(fn);
            Value decorated = call(decorator, a, new LinkedHashMap<>(), node.position());
            if (decorated instanceof FunctionValue || decorated instanceof BuiltinFunction) fn = decorated;
        }
        env.define(node.name(), fn);
        return NoneValue.NONE;
    }

    @Override public Value visitRouteDefinition(RouteDefinition node) {
        env.define(node.endpoint(), new FunctionValue(node.function(), env));
        return NoneValue.NONE;
    }

    @Override public Value visitReturn(ReturnStatement node) {
        throw new ReturnSignal(node.value() == null ? NoneValue.NONE : node.value().accept(this));
    }

    @Override public Value visitIf(IfStatement node) {
        for (ConditionalBranch b : node.branches()) {
            if (b.condition().accept(this).isTruthy()) {
                executeBlock(b.body());
                return NoneValue.NONE;
            }
        }
        executeBlock(node.elseBody());
        return NoneValue.NONE;
    }

    @Override public Value visitFor(ForStatement node) {
        lastPosition = node.position();
        List<Value> items = new ArrayList<>(iterate(node.iterable().accept(this)));
        for (Value item : items) {
            step(node.position());
            if (node.targets().size() == 1) {
                env.define(node.targets().get(0), item);
            } else {
                List<Value> parts = iterate(item);
                if (parts.size() != node.targets().size()) throw fail("EVAL004", "ValueError", "cannot unpack " + parts.size() + " values into " + node.targets().size() + " targets");
                for (int i = 0; i < parts.size(); i++) env.define(node.targets().get(i), parts.get(i));
            }
            executeBlock(node.body());
        }
        return NoneValue.NONE;
    }

    @Override public Value visitExpressionStatement(ExpressionStatement node) {
        node.expression().accept(this);
        return NoneValue.NONE;
    }

    @Override public Value visitPass(PassStatement node) { return NoneValue.NONE; }
    @Override public Value visitParameter(Parameter node) { return NoneValue.NONE; }
    @Override public Value visitConditionalBranch(ConditionalBranch node) { return NoneValue.NONE; }
    @Override public Value visitImportedName(ImportedName node) { return NoneValue.NONE; }

    // ----------------------------------------------------------- expressions

    @Override public Value visitIdentifier(Identifier node) {
        lastPosition = node.position();
        Value v = env.get(node.name());
        if (v == null) throw fail("EVAL001", "NameError", "name '" + node.name() + "' is not defined at evaluation time");
        return v;
    }

    @Override public Value visitLiteral(Literal node) {
        switch (node.kind()) {
            case STRING: return new StringValue(node.stringValue());
            case INTEGER: return NumberValue.ofLong((Long) node.value());
            case FLOAT: return NumberValue.ofDouble((Double) node.value());
            case BOOLEAN: return BooleanValue.of(((Boolean) node.value()).booleanValue());
            default: return NoneValue.NONE;
        }
    }

    @Override public Value visitListLiteral(ListLiteral node) {
        List<Value> items = new ArrayList<>();
        for (PythonExpression e : node.elements()) items.add(e.accept(this));
        return new ListValue(items);
    }

    @Override public Value visitDictionaryLiteral(DictionaryLiteral node) {
        DictValue d = new DictValue();
        for (DictionaryEntry e : node.entries()) d.put(e.key().accept(this).asText(), e.value().accept(this));
        return d;
    }

    @Override public Value visitDictionaryEntry(DictionaryEntry node) { return node.value().accept(this); }

    @Override public Value visitAttributeAccess(AttributeAccess node) {
        Value target = node.target().accept(this);
        lastPosition = node.position();
        String attr = node.attribute();
        if (target instanceof ObjectValue) {
            Value v = ((ObjectValue) target).get(attr);
            if (v == null) throw fail("EVAL002", "AttributeError", "'" + target.typeName() + "' object has no attribute '" + attr + "'");
            return v;
        }
        if (target instanceof ExternalValue) return new ExternalValue(((ExternalValue) target).name() + "." + attr);
        Value method = boundMethod(target, attr);
        if (method != null) return method;
        throw fail("EVAL002", "AttributeError", "'" + target.typeName() + "' object has no attribute '" + attr + "'");
    }

    /** Methods of str / list / dict as bound builtins. */
    private Value boundMethod(Value target, String name) {
        if (target instanceof StringValue) {
            String s = ((StringValue) target).value();
            switch (name) {
                case "upper": return builtin(name, (a, k) -> new StringValue(s.toUpperCase()));
                case "lower": return builtin(name, (a, k) -> new StringValue(s.toLowerCase()));
                case "strip": return builtin(name, (a, k) -> new StringValue(s.trim()));
                case "lstrip": return builtin(name, (a, k) -> new StringValue(s.replaceAll("^\\s+", "")));
                case "rstrip": return builtin(name, (a, k) -> new StringValue(s.replaceAll("\\s+$", "")));
                case "title": return builtin(name, (a, k) -> new StringValue(titleCase(s)));
                case "capitalize": return builtin(name, (a, k) -> new StringValue(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase()));
                case "replace": return builtin(name, (a, k) -> new StringValue(s.replace(arg(a, 0, name).asText(), arg(a, 1, name).asText())));
                case "split": return builtin(name, (a, k) -> {
                    String[] parts = a.isEmpty() ? s.trim().split("\\s+") : s.split(java.util.regex.Pattern.quote(a.get(0).asText()), -1);
                    List<Value> out = new ArrayList<>();
                    for (String p : parts) if (!(a.isEmpty() && p.isEmpty())) out.add(new StringValue(p));
                    return new ListValue(out);
                });
                case "startswith": return builtin(name, (a, k) -> BooleanValue.of(s.startsWith(arg(a, 0, name).asText())));
                case "endswith": return builtin(name, (a, k) -> BooleanValue.of(s.endsWith(arg(a, 0, name).asText())));
                case "isdigit": return builtin(name, (a, k) -> BooleanValue.of(!s.isEmpty() && s.chars().allMatch(Character::isDigit)));
                case "find": return builtin(name, (a, k) -> NumberValue.ofLong(s.indexOf(arg(a, 0, name).asText())));
                case "count": return builtin(name, (a, k) -> NumberValue.ofLong(countOccurrences(s, arg(a, 0, name).asText())));
                case "join": return builtin(name, (a, k) -> {
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (Value v : iterate(arg(a, 0, name))) { if (!first) sb.append(s); first = false; sb.append(v.asText()); }
                    return new StringValue(sb.toString());
                });
                case "format": return builtin(name, (a, k) -> {
                    String out = s;
                    for (Value v : a) out = out.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(v.asText()));
                    for (Map.Entry<String, Value> e : k.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue().asText());
                    return new StringValue(out);
                });
                default: return null;
            }
        }
        if (target instanceof ListValue) {
            List<Value> items = ((ListValue) target).items();
            switch (name) {
                case "append": return builtin(name, (a, k) -> { items.add(arg(a, 0, name)); return NoneValue.NONE; });
                case "extend": return builtin(name, (a, k) -> { items.addAll(iterate(arg(a, 0, name))); return NoneValue.NONE; });
                case "insert": return builtin(name, (a, k) -> { items.add((int) number(arg(a, 0, name), name).longValue(), arg(a, 1, name)); return NoneValue.NONE; });
                case "remove": return builtin(name, (a, k) -> {
                    Value x = arg(a, 0, name);
                    for (int i = 0; i < items.size(); i++) if (items.get(i).equalsValue(x)) { items.remove(i); return NoneValue.NONE; }
                    throw fail("EVAL004", "ValueError", "list.remove(x): x not in list");
                });
                case "pop": return builtin(name, (a, k) -> {
                    if (items.isEmpty()) throw fail("EVAL004", "IndexError", "pop from empty list");
                    int i = a.isEmpty() ? items.size() - 1 : listIndex(items.size(), a.get(0));
                    return items.remove(i);
                });
                case "index": return builtin(name, (a, k) -> {
                    Value x = arg(a, 0, name);
                    for (int i = 0; i < items.size(); i++) if (items.get(i).equalsValue(x)) return NumberValue.ofLong(i);
                    throw fail("EVAL004", "ValueError", x.repr() + " is not in list");
                });
                case "count": return builtin(name, (a, k) -> {
                    long n = 0;
                    for (Value v : items) if (v.equalsValue(arg(a, 0, name))) n++;
                    return NumberValue.ofLong(n);
                });
                case "clear": return builtin(name, (a, k) -> { items.clear(); return NoneValue.NONE; });
                case "reverse": return builtin(name, (a, k) -> { java.util.Collections.reverse(items); return NoneValue.NONE; });
                case "sort": return builtin(name, (a, k) -> {
                    items.sort(this::compareValues);
                    if (k.containsKey("reverse") && k.get("reverse").isTruthy()) java.util.Collections.reverse(items);
                    return NoneValue.NONE;
                });
                case "copy": return builtin(name, (a, k) -> new ListValue(items));
                default: return null;
            }
        }
        if (target instanceof DictValue) {
            DictValue d = (DictValue) target;
            switch (name) {
                case "get": return builtin(name, (a, k) -> {
                    Value v = d.get(arg(a, 0, name).asText());
                    return v != null ? v : a.size() > 1 ? a.get(1) : NoneValue.NONE;
                });
                case "keys": return builtin(name, (a, k) -> new ListValue(iterate(d)));
                case "values": return builtin(name, (a, k) -> new ListValue(new ArrayList<>(d.entries().values())));
                case "items": return builtin(name, (a, k) -> {
                    List<Value> out = new ArrayList<>();
                    for (Map.Entry<String, Value> e : d.entries().entrySet()) {
                        List<Value> pair = new ArrayList<>();
                        pair.add(new StringValue(e.getKey()));
                        pair.add(e.getValue());
                        out.add(new ListValue(pair));
                    }
                    return new ListValue(out);
                });
                case "pop": return builtin(name, (a, k) -> {
                    Value v = d.entries().remove(arg(a, 0, name).asText());
                    if (v == null) { if (a.size() > 1) return a.get(1); throw fail("EVAL003", "KeyError", arg(a, 0, name).repr()); }
                    return v;
                });
                case "update": return builtin(name, (a, k) -> {
                    if (!a.isEmpty() && a.get(0) instanceof DictValue) d.entries().putAll(((DictValue) a.get(0)).entries());
                    d.entries().putAll(k);
                    return NoneValue.NONE;
                });
                case "setdefault": return builtin(name, (a, k) -> {
                    String key = arg(a, 0, name).asText();
                    if (!d.has(key)) d.put(key, a.size() > 1 ? a.get(1) : NoneValue.NONE);
                    return d.get(key);
                });
                case "copy": return builtin(name, (a, k) -> new DictValue(d.entries()));
                default: return null;
            }
        }
        return null;
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) { sb.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c)); start = false; }
            else { sb.append(c); start = true; }
        }
        return sb.toString();
    }

    private static long countOccurrences(String s, String sub) {
        if (sub.isEmpty()) return s.length() + 1;
        long n = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    private int listIndex(int size, Value index) {
        int i = (int) number(index, "index").longValue();
        if (i < 0) i += size;
        if (i < 0 || i >= size) throw fail("EVAL003", "IndexError", "list index out of range");
        return i;
    }

    @Override public Value visitSubscriptAccess(SubscriptAccess node) {
        Value target = node.target().accept(this);
        Value index = node.index().accept(this);
        lastPosition = node.position();
        if (target instanceof DictValue) {
            Value v = ((DictValue) target).get(index.asText());
            if (v == null) throw fail("EVAL003", "KeyError", index.repr());
            return v;
        }
        if (target instanceof ListValue) {
            List<Value> items = ((ListValue) target).items();
            return items.get(listIndex(items.size(), index));
        }
        if (target instanceof StringValue) {
            String s = ((StringValue) target).value();
            return new StringValue(String.valueOf(s.charAt(listIndex(s.length(), index))));
        }
        throw fail("EVAL003", "TypeError", "'" + target.typeName() + "' object is not subscriptable");
    }

    @Override public Value visitCall(CallExpression node) {
        Value callee = node.callee().accept(this);
        List<Value> args = new ArrayList<>();
        for (PythonExpression a : node.arguments()) args.add(a.accept(this));
        Map<String, Value> kwargs = new LinkedHashMap<>();
        for (KeywordArgument k : node.keywordArguments()) kwargs.put(k.name(), k.value().accept(this));
        return call(callee, args, kwargs, node.position());
    }

    @Override public Value visitKeywordArgument(KeywordArgument node) { return node.value().accept(this); }

    @Override public Value visitRenderTemplateCall(RenderTemplateCall node) {
        Map<String, Value> context = new LinkedHashMap<>();
        for (KeywordArgument k : node.contextArguments()) context.put(k.name(), k.value().accept(this));
        renders.add(new RenderInvocation(node.templateName(), context));
        return new ResponseValue(ResponseValue.Kind.RENDER, node.templateName());
    }

    @Override public Value visitBinary(BinaryExpression node) {
        Value l = node.left().accept(this);
        Value r = node.right().accept(this);
        return arithmetic(node.operator(), l, r, node.position());
    }

    private Value arithmetic(String op, Value l, Value r, SourcePosition pos) {
        lastPosition = pos;
        if ((l instanceof NumberValue || l instanceof BooleanValue) && (r instanceof NumberValue || r instanceof BooleanValue)) {
            NumberValue a = number(l, op);
            NumberValue b = number(r, op);
            boolean ints = a.isInteger() && b.isInteger();
            switch (op) {
                case "+": return ints ? NumberValue.ofLong(a.longValue() + b.longValue()) : NumberValue.ofDouble(a.doubleValue() + b.doubleValue());
                case "-": return ints ? NumberValue.ofLong(a.longValue() - b.longValue()) : NumberValue.ofDouble(a.doubleValue() - b.doubleValue());
                case "*": return ints ? NumberValue.ofLong(a.longValue() * b.longValue()) : NumberValue.ofDouble(a.doubleValue() * b.doubleValue());
                case "/":
                    if (b.doubleValue() == 0) throw fail("EVAL004", "ZeroDivisionError", "division by zero");
                    return NumberValue.ofDouble(a.doubleValue() / b.doubleValue());
                case "//":
                    if (b.doubleValue() == 0) throw fail("EVAL004", "ZeroDivisionError", "division by zero");
                    return ints ? NumberValue.ofLong(Math.floorDiv(a.longValue(), b.longValue())) : NumberValue.ofDouble(Math.floor(a.doubleValue() / b.doubleValue()));
                case "%":
                    if (b.doubleValue() == 0) throw fail("EVAL004", "ZeroDivisionError", "modulo by zero");
                    return ints ? NumberValue.ofLong(Math.floorMod(a.longValue(), b.longValue())) : NumberValue.ofDouble(a.doubleValue() - b.doubleValue() * Math.floor(a.doubleValue() / b.doubleValue()));
                default: break;
            }
        }
        if (op.equals("+") && l instanceof StringValue && r instanceof StringValue) return new StringValue(l.asText() + r.asText());
        if (op.equals("+") && l instanceof ListValue && r instanceof ListValue) {
            List<Value> items = new ArrayList<>(((ListValue) l).items());
            items.addAll(((ListValue) r).items());
            return new ListValue(items);
        }
        if (op.equals("*") && l instanceof StringValue && r instanceof NumberValue) return new StringValue(l.asText().repeat((int) Math.max(0, ((NumberValue) r).longValue())));
        throw fail("EVAL004", "TypeError", "unsupported operand type(s) for " + op + ": '" + l.typeName() + "' and '" + r.typeName() + "'");
    }

    @Override public Value visitComparison(ComparisonExpression node) {
        Value l = node.left().accept(this);
        Value r = node.right().accept(this);
        lastPosition = node.position();
        switch (node.operator()) {
            case "==": return BooleanValue.of(l.equalsValue(r));
            case "!=": return BooleanValue.of(!l.equalsValue(r));
            case "<": return BooleanValue.of(compareValues(l, r) < 0);
            case ">": return BooleanValue.of(compareValues(l, r) > 0);
            case "<=": return BooleanValue.of(compareValues(l, r) <= 0);
            case ">=": return BooleanValue.of(compareValues(l, r) >= 0);
            case "in": return BooleanValue.of(contains(r, l));
            case "not in": return BooleanValue.of(!contains(r, l));
            case "is": return BooleanValue.of(l == r || (l.isNone() && r.isNone()));
            case "is not": return BooleanValue.of(!(l == r || (l.isNone() && r.isNone())));
            default: throw fail("EVAL004", "TypeError", "unsupported comparison " + node.operator());
        }
    }

    private boolean contains(Value container, Value item) {
        if (container instanceof StringValue) return container.asText().contains(item.asText());
        if (container instanceof DictValue) return ((DictValue) container).has(item.asText());
        for (Value v : iterate(container)) if (v.equalsValue(item)) return true;
        return false;
    }

    @Override public Value visitLogical(LogicalExpression node) {
        Value l = node.left().accept(this);
        if (node.operator().equals("and")) return l.isTruthy() ? node.right().accept(this) : l;
        return l.isTruthy() ? l : node.right().accept(this);
    }

    @Override public Value visitUnary(UnaryExpression node) {
        Value v = node.operand().accept(this);
        lastPosition = node.position();
        switch (node.operator()) {
            case "not": return BooleanValue.of(!v.isTruthy());
            case "-": {
                NumberValue n = number(v, "-");
                return n.isInteger() ? NumberValue.ofLong(-n.longValue()) : NumberValue.ofDouble(-n.doubleValue());
            }
            default: return number(v, "+");
        }
    }

    // ------------------------------------------- shared helpers for the template renderer

    /** A bound str/list/dict method as a builtin, or null when the value has no such method. */
    public Value methodOf(Value target, String name, SourcePosition pos) {
        lastPosition = pos;
        return boundMethod(target, name);
    }

    public Value binary(String op, Value l, Value r, SourcePosition pos) { return arithmetic(op, l, r, pos); }

    public int compare(Value a, Value b, SourcePosition pos) {
        lastPosition = pos;
        return compareValues(a, b);
    }

    public boolean containsValue(Value container, Value item, SourcePosition pos) {
        lastPosition = pos;
        return contains(container, item);
    }

    public List<Value> iterateValue(Value v, SourcePosition pos) {
        lastPosition = pos;
        return iterate(v);
    }

    public NumberValue asNumber(Value v, SourcePosition pos) {
        lastPosition = pos;
        return number(v, "expression");
    }
}
