package fjc.semantic;

import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;
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
import fjc.python.ast.PythonExpression;
import fjc.python.ast.PythonProgram;
import fjc.python.ast.PythonStatement;
import fjc.python.ast.RenderTemplateCall;
import fjc.python.ast.ReturnStatement;
import fjc.python.ast.RouteDefinition;
import fjc.python.ast.SubscriptAccess;
import fjc.python.ast.UnaryExpression;
import fjc.symbols.Scope;
import fjc.symbols.Symbol;
import fjc.symbols.SymbolKind;
import fjc.symbols.SymbolTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic analysis of the Python/Flask side. Fills the symbol table and
 * produces a {@link FlaskModel}. Error codes:
 * <ul>
 *   <li>PY001 UndefinedVariable</li>
 *   <li>PY002 DuplicateRoute (same URL rule and overlapping HTTP method)</li>
 *   <li>PY003 TemplateNotFound (render_template of a missing file)</li>
 *   <li>PY004 MissingContextVariable (emitted by {@link CrossLanguageAnalyzer})</li>
 *   <li>PY005 InvalidDataShape (product data: not a list of dicts, inconsistent keys, wrong field types, duplicate ids)</li>
 *   <li>PY006 DuplicateDefinition (function / route defined twice)</li>
 *   <li>PY007 DuplicateDictionaryKey</li>
 *   <li>PY008 InvalidRenderTemplateCall (non-literal template name, extra positional args)</li>
 *   <li>PY009 InvalidHttpMethod</li>
 *   <li>PY010 RouteParameterMismatch (URL parameters vs function parameters)</li>
 *   <li>PY011 UnreachableCode (warning)</li>
 *   <li>PY012 UnknownAttribute (unknown attribute of a known Flask object)</li>
 *   <li>PY013 ArgumentCountMismatch (call of a user function with the wrong number of arguments)</li>
 *   <li>PY014 MissingFlaskApp (route decorator on an object that is not a Flask application)</li>
 *   <li>PY015 RouteWithoutReturn (route function never returns a response)</li>
 * </ul>
 */
// التحقق من تعريفات بايثون ومسارات Flask وأنواع البيانات والقوالب المستدعاة.
public final class PythonSemanticAnalyzer {

    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Set<String> PYTHON_BUILTINS = Set.of(
            "print", "len", "int", "float", "str", "bool", "range", "list", "dict", "min", "max", "abs", "round",
            "sorted", "sum", "isinstance", "__name__", "enumerate", "zip", "type", "repr", "any", "all");

    private final ErrorCollector errors;
    private final SymbolTable symbols;
    private final List<String> templateFiles;
    private final List<String> staticFiles;

    private final Map<String, ValueShape> moduleShapes = new LinkedHashMap<>();
    private final Map<String, FunctionDefinition> functions = new LinkedHashMap<>();
    private final Set<String> moduleNames = new LinkedHashSet<>();
    private final Set<String> flaskAppNames = new LinkedHashSet<>();
    private ShapeInferencer inferencer;
    private FlaskModel model;
    private Scope moduleScope;

    public PythonSemanticAnalyzer(ErrorCollector errors, SymbolTable symbols, List<String> templateFiles, List<String> staticFiles) {
        this.errors = errors;
        this.symbols = symbols;
        this.templateFiles = templateFiles;
        this.staticFiles = staticFiles;
    }

    // يفحص بايثون على مرحلتين: تعريفات الوحدة أولا ثم أجسام الدوال، مع بناء نموذج لمسارات التطبيق.
    public FlaskModel analyze(PythonProgram program) {
        model = new FlaskModel(templateFiles, staticFiles);
        inferencer = new ShapeInferencer(moduleShapes, functions);
        defineBuiltins();
        moduleScope = symbols.enter(program.moduleFileName(), "module");

        // pass 1: module-level statements in source order (declarations + order-sensitive references)
        for (PythonStatement s : program.statements()) analyzeModuleStatement(s);

        // pass 2: function bodies (they may reference any module-level name)
        for (PythonStatement s : program.statements()) {
            if (s instanceof FunctionDefinition) analyzeFunction((FunctionDefinition) s, null);
            else if (s instanceof RouteDefinition) analyzeRoute((RouteDefinition) s);
        }
        checkDuplicateRoutes(program);
        symbols.exit();
        for (String n : moduleNames) model.addModuleName(n);
        return model;
    }

    // ------------------------------------------------------------ builtins

    private void defineBuiltins() {
        for (Map.Entry<String, ValueShape> e : ShapeInferencer.builtinShapes().entrySet()) {
            if (PYTHON_BUILTINS.contains(e.getKey())) {
                symbols.define(e.getKey(), SymbolKind.BUILTIN, e.getValue(), SourcePosition.unknown("<builtin>"), "Python builtin");
            }
        }
    }

    private boolean isBuiltin(String name) { return PYTHON_BUILTINS.contains(name); }

    // --------------------------------------------------------- module level

    private void analyzeModuleStatement(PythonStatement s) {
        if (s instanceof ImportStatement) {
            ImportStatement imp = (ImportStatement) s;
            for (ImportedName n : imp.names()) {
                String bound = n.boundName();
                ValueShape shape = ShapeInferencer.builtinShapes().getOrDefault(n.name(), ValueShape.ANY);
                if (!imp.isFromImport()) shape = ValueShape.object("module " + n.name(), null);
                defineModule(bound, SymbolKind.IMPORT, shape, n.position(), "from " + imp.module());
            }
            return;
        }
        if (s instanceof FunctionDefinition) {
            FunctionDefinition fn = (FunctionDefinition) s;
            for (PythonExpression dec : fn.decorators()) checkExpression(dec, new Ctx(null, moduleNames));
            declareFunction(fn, SymbolKind.FUNCTION, "def " + fn.name() + "(" + String.join(", ", fn.parameterNames()) + ")");
            return;
        }
        if (s instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) s;
            String appName = route.applicationName();
            if (!moduleNames.contains(appName)) {
                errors.error(ErrorCategory.PYTHON, "PY001", "UndefinedVariable",
                        "Name '" + appName + "' used in @" + appName + ".route is not defined", route.position());
            } else if (!flaskAppNames.contains(appName)) {
                errors.error(ErrorCategory.PYTHON, "PY014", "MissingFlaskApp",
                        "'" + appName + "' is not a Flask application instance (expected '" + appName + " = Flask(__name__)')",
                        route.position());
            }
            checkRouteDecorator(route);
            declareFunction(route.function(), SymbolKind.ROUTE, route.rule() + " " + route.methods());
            model.addRoute(route);
            return;
        }
        if (s instanceof Assignment) {
            Assignment a = (Assignment) s;
            Ctx ctx = new Ctx(null, moduleNames);
            checkExpression(a.value(), ctx);
            if (a.targetName() != null) {
                ValueShape shape = inferencer.infer(a.value(), null);
                if (a.isAugmented()) {
                    checkIdentifierDefined(a.targetName(), a.target().position(), ctx);
                    shape = moduleShapes.getOrDefault(a.targetName(), ValueShape.ANY).union(shape);
                }
                if (isFlaskConstructor(a.value())) flaskAppNames.add(a.targetName());
                validateData(a.targetName(), a.value());
                defineModule(a.targetName(), SymbolKind.MODULE_VARIABLE, shape, a.position(), shape.describe());
            } else {
                checkExpression(a.target(), ctx);
            }
            return;
        }
        if (s instanceof IfStatement) {
            IfStatement i = (IfStatement) s;
            Ctx ctx = new Ctx(null, moduleNames);
            for (ConditionalBranch b : i.branches()) {
                checkExpression(b.condition(), ctx);
                for (PythonStatement inner : b.body()) analyzeModuleStatement(inner);
            }
            for (PythonStatement inner : i.elseBody()) analyzeModuleStatement(inner);
            return;
        }
        if (s instanceof ExpressionStatement) {
            checkExpression(((ExpressionStatement) s).expression(), new Ctx(null, moduleNames));
            return;
        }
        if (s instanceof ForStatement) {
            ForStatement f = (ForStatement) s;
            Ctx ctx = new Ctx(null, moduleNames);
            checkExpression(f.iterable(), ctx);
            ValueShape it = inferencer.infer(f.iterable(), null);
            for (String t : f.targets()) defineModule(t, SymbolKind.LOOP_VARIABLE, it.isList() ? it.element() : ValueShape.ANY, f.position(), "for-loop target");
            for (PythonStatement inner : f.body()) analyzeModuleStatement(inner);
            return;
        }
        if (s instanceof ReturnStatement) {
            errors.error(ErrorCategory.PYTHON, "PY001", "ReturnOutsideFunction", "'return' outside function", s.position());
        }
    }

    private boolean isFlaskConstructor(PythonExpression value) {
        return value instanceof CallExpression && "Flask".equals(((CallExpression) value).calleeName());
    }

    private void defineModule(String name, SymbolKind kind, ValueShape shape, SourcePosition pos, String detail) {
        moduleNames.add(name);
        moduleShapes.put(name, shape);
        symbols.define(name, kind, shape, pos, detail);
    }

    private void declareFunction(FunctionDefinition fn, SymbolKind kind, String detail) {
        if (functions.containsKey(fn.name())) {
            FunctionDefinition first = functions.get(fn.name());
            errors.error(ErrorCategory.PYTHON, "PY006", "DuplicateDefinition",
                    "Function '" + fn.name() + "' is defined more than once (first definition at line " + first.line() + ")",
                    fn.position());
        }
        functions.put(fn.name(), fn);
        defineModule(fn.name(), kind, ValueShape.function(fn.name()), fn.position(), detail);
    }

    // ---------------------------------------------------------------- routes

    private void checkRouteDecorator(RouteDefinition route) {
        for (String m : route.methods()) {
            if (!HTTP_METHODS.contains(m.toUpperCase()) || !m.equals(m.toUpperCase())) {
                errors.error(ErrorCategory.PYTHON, "PY009", "InvalidHttpMethod",
                        "'" + m + "' is not a valid HTTP method for route '" + route.rule() + "' (expected one of "
                                + HTTP_METHODS + ")", route.methodsPosition());
            }
        }
        if (!route.rule().startsWith("/")) {
            errors.error(ErrorCategory.PYTHON, "PY002", "InvalidRouteRule",
                    "Route rule '" + route.rule() + "' must start with '/'", route.position());
        }
        for (RouteDefinition.RouteParameter p : route.parameters()) {
            if (!Set.of("string", "int", "float", "path", "uuid", "any").contains(p.converter())) {
                errors.error(ErrorCategory.PYTHON, "PY010", "RouteParameterMismatch",
                        "Unknown URL converter '" + p.converter() + "' in route '" + route.rule() + "'", route.position());
            }
        }
        Set<String> urlParams = new LinkedHashSet<>();
        for (RouteDefinition.RouteParameter p : route.parameters()) urlParams.add(p.name());
        Set<String> fnParams = new LinkedHashSet<>(route.function().parameterNames());
        if (!urlParams.equals(fnParams)) {
            errors.error(ErrorCategory.PYTHON, "PY010", "RouteParameterMismatch",
                    "Route '" + route.rule() + "' declares URL parameters " + urlParams + " but function '"
                            + route.endpoint() + "' has parameters " + fnParams, route.function().position());
        }
    }

    // خطأ تكرار المسار: مساران بنفس العنوان وطريقة الطلب يؤديان إلى تشخيص دلالي.
    private void checkDuplicateRoutes(PythonProgram program) {
        Map<String, RouteDefinition> seen = new HashMap<>();
        for (RouteDefinition route : program.routes()) {
            String key = normalizeRule(route.rule());
            for (String m : route.methods()) {
                String k = key + " " + m.toUpperCase();
                RouteDefinition first = seen.get(k);
                if (first != null) {
                    errors.error(ErrorCategory.PYTHON, "PY002", "DuplicateRoute",
                            "Route '" + route.rule() + "' with method " + m + " is already handled by '" + first.endpoint()
                                    + "' (line " + first.line() + ")", route.position());
                } else {
                    seen.put(k, route);
                }
            }
        }
    }

    static String normalizeRule(String rule) {
        String r = rule.replaceAll("<[^>]*>", "<>");
        if (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private void analyzeRoute(RouteDefinition route) {
        FunctionDefinition fn = route.function();
        Map<String, ValueShape> params = new LinkedHashMap<>();
        for (RouteDefinition.RouteParameter p : route.parameters()) {
            switch (p.converter()) {
                case "int": params.put(p.name(), ValueShape.INTEGER); break;
                case "float": params.put(p.name(), ValueShape.FLOAT); break;
                default: params.put(p.name(), ValueShape.STRING);
            }
        }
        inferencer.setParameterShapes(fn.name(), params);
        FlaskModel.RouteInfo info = model.route(route.endpoint());
        analyzeFunction(fn, info);
        if (!containsReturn(fn.body())) {
            errors.error(ErrorCategory.PYTHON, "PY015", "RouteWithoutReturn",
                    "Route function '" + fn.name() + "' never returns a response", fn.position());
        }
    }

    private static boolean containsReturn(List<PythonStatement> body) {
        for (PythonStatement s : body) {
            if (s instanceof ReturnStatement) return true;
            if (s instanceof ForStatement && containsReturn(((ForStatement) s).body())) return true;
            if (s instanceof IfStatement) {
                for (ConditionalBranch b : ((IfStatement) s).branches()) if (containsReturn(b.body())) return true;
                if (containsReturn(((IfStatement) s).elseBody())) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- functions

    /** Analysis context: the enclosing function (null at module level) and the visible names. */
    private static final class Ctx {
        final FunctionDefinition function;
        final Set<String> visible;
        final Map<String, ValueShape> locals;

        Ctx(FunctionDefinition function, Set<String> visible) { this(function, visible, new HashMap<>()); }

        Ctx(FunctionDefinition function, Set<String> visible, Map<String, ValueShape> locals) {
            this.function = function;
            this.visible = visible;
            this.locals = locals;
        }
    }

    private void analyzeFunction(FunctionDefinition fn, FlaskModel.RouteInfo route) {
        Scope scope = symbols.enter(fn.name(), route != null ? "route" : "function");
        Map<String, ValueShape> locals = inferencer.collectLocals(fn);
        Set<String> visible = new LinkedHashSet<>(moduleNames);
        Set<String> localNames = new LinkedHashSet<>(fn.parameterNames());
        collectAssignedNames(fn.body(), localNames);
        visible.addAll(localNames);
        for (Parameter p : fn.parameters()) {
            symbols.define(p.name(), SymbolKind.PARAMETER, locals.getOrDefault(p.name(), ValueShape.ANY), p.position(), "parameter");
            if (p.defaultValue() != null) checkExpression(p.defaultValue(), new Ctx(null, moduleNames));
        }
        Ctx ctx = new Ctx(fn, visible, locals);
        analyzeBlock(fn.body(), ctx, route, scope);
        if (route != null) for (String n : localNames) model.addLocalName(route, n);
        symbols.exit();
    }

    private static void collectAssignedNames(List<PythonStatement> body, Set<String> out) {
        for (PythonStatement s : body) {
            if (s instanceof Assignment && ((Assignment) s).targetName() != null) out.add(((Assignment) s).targetName());
            else if (s instanceof ForStatement) { out.addAll(((ForStatement) s).targets()); collectAssignedNames(((ForStatement) s).body(), out); }
            else if (s instanceof IfStatement) {
                for (ConditionalBranch b : ((IfStatement) s).branches()) collectAssignedNames(b.body(), out);
                collectAssignedNames(((IfStatement) s).elseBody(), out);
            }
        }
    }

    private void analyzeBlock(List<PythonStatement> body, Ctx ctx, FlaskModel.RouteInfo route, Scope scope) {
        boolean returned = false;
        for (PythonStatement s : body) {
            if (returned) {
                errors.warning(ErrorCategory.PYTHON, "PY011", "UnreachableCode",
                        "Statement is unreachable (it follows a 'return')", s.position());
                returned = false;
            }
            if (s instanceof Assignment) {
                Assignment a = (Assignment) s;
                checkExpression(a.value(), ctx);
                if (a.targetName() != null) {
                    if (a.isAugmented()) checkIdentifierDefined(a.targetName(), a.target().position(), ctx);
                    if (scope.lookupLocal(a.targetName()) == null) {
                        symbols.define(a.targetName(), SymbolKind.LOCAL_VARIABLE,
                                ctx.locals.getOrDefault(a.targetName(), ValueShape.ANY), a.position(), "local assignment");
                    }
                    validateData(a.targetName(), a.value());
                } else {
                    checkExpression(a.target(), ctx);
                }
            } else if (s instanceof ReturnStatement) {
                ReturnStatement r = (ReturnStatement) s;
                if (r.value() != null) checkExpression(r.value(), ctx);
                returned = true;
            } else if (s instanceof ExpressionStatement) {
                checkExpression(((ExpressionStatement) s).expression(), ctx);
            } else if (s instanceof IfStatement) {
                IfStatement i = (IfStatement) s;
                for (ConditionalBranch b : i.branches()) {
                    checkExpression(b.condition(), ctx);
                    analyzeBlock(b.body(), ctx, route, scope);
                }
                analyzeBlock(i.elseBody(), ctx, route, scope);
            } else if (s instanceof ForStatement) {
                ForStatement f = (ForStatement) s;
                checkExpression(f.iterable(), ctx);
                ValueShape it = inferencer.infer(f.iterable(), ctx.locals);
                if (!it.isIterable() && !it.isString()) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "Cannot iterate over a value of type " + it.describe(), f.iterable().position());
                }
                Scope loop = symbols.enter("for " + String.join(", ", f.targets()), "loop");
                for (String t : f.targets()) {
                    symbols.define(t, SymbolKind.LOOP_VARIABLE, ctx.locals.getOrDefault(t, ValueShape.ANY), f.position(), "loop variable");
                }
                analyzeBlock(f.body(), ctx, route, loop);
                symbols.exit();
            } else if (s instanceof FunctionDefinition) {
                errors.error(ErrorCategory.PYTHON, "PY006", "NestedFunction",
                        "Nested function definitions are not supported by the Flask subset", s.position());
            }
            // collect render_template calls of routes
            if (route != null) collectRenderCalls(s, ctx, route);
        }
    }

    private void collectRenderCalls(PythonStatement s, Ctx ctx, FlaskModel.RouteInfo route) {
        List<RenderTemplateCall> calls = new ArrayList<>();
        findRenderCalls(s, calls);
        for (RenderTemplateCall call : calls) {
            Map<String, ValueShape> context = new LinkedHashMap<>();
            for (KeywordArgument k : call.contextArguments()) context.put(k.name(), inferencer.infer(k.value(), ctx.locals));
            model.addRenderCall(route, call, context);
        }
    }

    private static void findRenderCalls(Object node, List<RenderTemplateCall> out) {
        if (node instanceof RenderTemplateCall) out.add((RenderTemplateCall) node);
        if (node instanceof fjc.ast.AstNode) {
            fjc.ast.AstNode n = (fjc.ast.AstNode) node;
            if (n instanceof IfStatement || n instanceof ForStatement || n instanceof ConditionalBranch) return; // handled per block
            for (fjc.ast.AstNode c : n.children()) findRenderCalls(c, out);
        }
    }

    // ----------------------------------------------------------- expressions

    private void checkExpression(PythonExpression e, Ctx ctx) {
        if (e instanceof Identifier) {
            checkIdentifierDefined(((Identifier) e).name(), e.position(), ctx);
        } else if (e instanceof Literal) {
            // nothing to check
        } else if (e instanceof ListLiteral) {
            for (PythonExpression x : ((ListLiteral) e).elements()) checkExpression(x, ctx);
        } else if (e instanceof DictionaryLiteral) {
            checkDictionary((DictionaryLiteral) e, ctx);
        } else if (e instanceof AttributeAccess) {
            AttributeAccess a = (AttributeAccess) e;
            checkExpression(a.target(), ctx);
            ValueShape target = inferencer.infer(a.target(), ctx.locals);
            if (target.isObject() && target.hasKnownFields() && !target.hasField(a.attribute())) {
                errors.error(ErrorCategory.PYTHON, "PY012", "UnknownAttribute",
                        "'" + target.objectName() + "' object has no attribute '" + a.attribute() + "' (known: "
                                + target.fields().keySet() + ")", a.position());
            }
        } else if (e instanceof SubscriptAccess) {
            checkExpression(((SubscriptAccess) e).target(), ctx);
            checkExpression(((SubscriptAccess) e).index(), ctx);
        } else if (e instanceof RenderTemplateCall) {
            checkRenderTemplate((RenderTemplateCall) e, ctx);
        } else if (e instanceof CallExpression) {
            checkCall((CallExpression) e, ctx);
        } else if (e instanceof BinaryExpression) {
            checkExpression(((BinaryExpression) e).left(), ctx);
            checkExpression(((BinaryExpression) e).right(), ctx);
        } else if (e instanceof ComparisonExpression) {
            checkExpression(((ComparisonExpression) e).left(), ctx);
            checkExpression(((ComparisonExpression) e).right(), ctx);
        } else if (e instanceof LogicalExpression) {
            checkExpression(((LogicalExpression) e).left(), ctx);
            checkExpression(((LogicalExpression) e).right(), ctx);
        } else if (e instanceof UnaryExpression) {
            checkExpression(((UnaryExpression) e).operand(), ctx);
        }
    }

    // عند استعمال اسم غير معرف في النطاق المتاح يظهر خطأ المتغير غير المعروف.
    private void checkIdentifierDefined(String name, SourcePosition pos, Ctx ctx) {
        if (ctx.visible.contains(name) || isBuiltin(name)) {
            Symbol sym = symbols.lookup(name);
            if (sym != null) sym.markReferenced();
            return;
        }
        String hint = ctx.function == null ? "" : " in function '" + ctx.function.name() + "'";
        errors.error(ErrorCategory.PYTHON, "PY001", "UndefinedVariable",
                "Name '" + name + "' is not defined" + hint, pos);
    }

    private void checkDictionary(DictionaryLiteral d, Ctx ctx) {
        Set<String> keys = new HashSet<>();
        for (DictionaryEntry entry : d.entries()) {
            checkExpression(entry.key(), ctx);
            checkExpression(entry.value(), ctx);
            String k = entry.keyName();
            if (k != null && !keys.add(k)) {
                errors.error(ErrorCategory.PYTHON, "PY007", "DuplicateDictionaryKey",
                        "Duplicate key '" + k + "' in dictionary literal", entry.position());
            }
        }
    }

    private void checkRenderTemplate(RenderTemplateCall call, Ctx ctx) {
        if (!templateFiles.contains(call.templateName())) {
            // إذا استدعى مسار قالبا غير موجود في مجلد القوالب يُسجل خطأ القالب المفقود.
            errors.error(ErrorCategory.PYTHON, "PY003", "TemplateNotFound",
                    "render_template() refers to template '" + call.templateName() + "' which does not exist in templates/ (available: "
                            + templateFiles + ")", call.templateNamePosition());
        }
        Set<String> names = new HashSet<>();
        for (KeywordArgument k : call.contextArguments()) {
            checkExpression(k.value(), ctx);
            if (!names.add(k.name())) {
                errors.error(ErrorCategory.PYTHON, "PY008", "InvalidRenderTemplateCall",
                        "Context variable '" + k.name() + "' is passed twice to render_template()", k.position());
            }
        }
        if (ctx.function == null) {
            errors.error(ErrorCategory.PYTHON, "PY008", "InvalidRenderTemplateCall",
                    "render_template() must be called inside a route function", call.position());
        }
    }

    private void checkCall(CallExpression call, Ctx ctx) {
        String name = call.calleeName();
        if ("render_template".equals(name)) {
            errors.error(ErrorCategory.PYTHON, "PY008", "InvalidRenderTemplateCall",
                    "render_template() requires a string literal template name as its first argument", call.position());
        }
        checkExpression(call.callee(), ctx);
        for (PythonExpression a : call.arguments()) checkExpression(a, ctx);
        for (KeywordArgument k : call.keywordArguments()) checkExpression(k.value(), ctx);
        if (call.callee() instanceof Identifier && functions.containsKey(name) && !ctx.locals.containsKey(name)) {
            FunctionDefinition fn = functions.get(name);
            int required = 0;
            for (Parameter p : fn.parameters()) if (p.defaultValue() == null) required++;
            int given = call.arguments().size() + call.keywordArguments().size();
            if (given < required || given > fn.parameters().size()) {
                errors.error(ErrorCategory.PYTHON, "PY013", "ArgumentCountMismatch",
                        "Function '" + name + "' expects " + (required == fn.parameters().size() ? required
                                : required + " to " + fn.parameters().size()) + " argument(s) but " + given + " were given", call.position());
            }
            for (KeywordArgument k : call.keywordArguments()) {
                if (!fn.parameterNames().contains(k.name())) {
                    errors.error(ErrorCategory.PYTHON, "PY013", "ArgumentCountMismatch",
                            "Function '" + name + "' has no parameter named '" + k.name() + "'", k.position());
                }
            }
        }
        if (call.callee() instanceof AttributeAccess) {
            AttributeAccess attr = (AttributeAccess) call.callee();
            if (attr.attribute().equals("route") && attr.target() instanceof Identifier) {
                String target = ((Identifier) attr.target()).name();
                if (moduleNames.contains(target) && !flaskAppNames.contains(target)) {
                    errors.error(ErrorCategory.PYTHON, "PY014", "MissingFlaskApp",
                            "'" + target + "' is not a Flask application instance (expected '" + target + " = Flask(__name__)')",
                            attr.position());
                }
            }
        }
    }

    // ------------------------------------------------------- data validation

    /**
     * PY005: validates product-like data. A list assigned to a module or local
     * variable must be homogeneous (all dicts with the same keys); the fields
     * {@code id}, {@code name} and {@code price} have fixed types; ids are unique.
     */
    private void validateData(String name, PythonExpression value) {
        if (!(value instanceof ListLiteral)) return;
        ListLiteral list = (ListLiteral) value;
        if (list.elements().isEmpty()) return;
        boolean anyDict = false;
        for (PythonExpression e : list.elements()) if (e instanceof DictionaryLiteral) anyDict = true;
        if (!anyDict) return;

        List<String> referenceKeys = null;
        Set<Object> ids = new HashSet<>();
        int index = 0;
        for (PythonExpression e : list.elements()) {
            if (!(e instanceof DictionaryLiteral)) {
                errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                        "'" + name + "' must be a list of dictionaries, but element " + index + " is "
                                + describeLiteral(e), e.position());
                index++;
                continue;
            }
            DictionaryLiteral d = (DictionaryLiteral) e;
            List<String> keys = d.keyNames();
            if (referenceKeys == null) {
                referenceKeys = keys;
            } else if (!new HashSet<>(keys).equals(new HashSet<>(referenceKeys))) {
                Set<String> missing = new LinkedHashSet<>(referenceKeys);
                missing.removeAll(keys);
                Set<String> extra = new LinkedHashSet<>(keys);
                extra.removeAll(referenceKeys);
                errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                        "Element " + index + " of '" + name + "' has keys " + keys + " but the first element has "
                                + referenceKeys + (missing.isEmpty() ? "" : " (missing: " + missing + ")")
                                + (extra.isEmpty() ? "" : " (unexpected: " + extra + ")"), d.position());
            }
            DictionaryEntry id = d.entry("id");
            if (id != null) {
                if (!(id.value() instanceof Literal) || ((Literal) id.value()).kind() != Literal.Kind.INTEGER) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "'id' of element " + index + " of '" + name + "' must be an integer literal, found "
                                    + describeLiteral(id.value()), id.value().position());
                } else if (!ids.add(((Literal) id.value()).value())) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "Duplicate id " + ((Literal) id.value()).value() + " in '" + name + "'", id.value().position());
                }
            }
            DictionaryEntry price = d.entry("price");
            if (price != null) {
                // تُفحص بنية بيانات المنتجات؛ مثال: السعر النصي بدلا من العدد خطأ دلالي.
                if (!(price.value() instanceof Literal) || !((Literal) price.value()).isNumber()) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "'price' of element " + index + " of '" + name + "' must be a number, found "
                                    + describeLiteral(price.value()), price.value().position());
                } else if (((Number) ((Literal) price.value()).value()).doubleValue() < 0) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "'price' of element " + index + " of '" + name + "' must not be negative", price.value().position());
                }
            }
            DictionaryEntry nm = d.entry("name");
            if (nm != null) {
                if (!(nm.value() instanceof Literal) || !((Literal) nm.value()).isString()) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "'name' of element " + index + " of '" + name + "' must be a string, found "
                                    + describeLiteral(nm.value()), nm.value().position());
                } else if (((Literal) nm.value()).stringValue().trim().isEmpty()) {
                    errors.error(ErrorCategory.PYTHON, "PY005", "InvalidDataShape",
                            "'name' of element " + index + " of '" + name + "' must not be empty", nm.value().position());
                }
            }
            index++;
        }
    }

    private static String describeLiteral(PythonExpression e) {
        if (e instanceof Literal) {
            Literal l = (Literal) e;
            switch (l.kind()) {
                case STRING: return "the string \"" + l.stringValue() + "\"";
                case INTEGER: return "the integer " + l.value();
                case FLOAT: return "the float " + l.value();
                case BOOLEAN: return "the boolean " + l.value();
                default: return "None";
            }
        }
        if (e instanceof ListLiteral) return "a list";
        if (e instanceof DictionaryLiteral) return "a dictionary";
        return "an expression (" + e.nodeName() + ")";
    }
}
