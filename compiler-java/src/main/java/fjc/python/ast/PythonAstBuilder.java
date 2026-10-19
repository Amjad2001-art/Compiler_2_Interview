package fjc.python.ast;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourcePosition;
import fjc.parsetree.ParseTreeNode;
import fjc.parsetree.RuleNode;
import fjc.parsetree.TokenNode;
import fjc.python.lexer.PythonTokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the compressed Python AST from the concrete parse tree.
 *
 * <p>What gets dropped on the way: every punctuation token ('(', ')', ',', ':',
 * '=', '[', ']', '{', '}'), keywords ('def', 'return', 'in', ...), NEWLINE /
 * INDENT / DEDENT tokens and every single-child wrapper production
 * (expression → or_test → and_test → not_test → comparison → arith_expr → term
 * → factor → primary → atom collapses into the one node that carries meaning).
 *
 * <p>What gets recognised: {@code @app.route(...)} decorators become
 * {@link RouteDefinition} nodes and {@code render_template("x", k=v)} calls become
 * {@link RenderTemplateCall} nodes.
 */
// تحويل شجرة التحليل النحوي لبايثون إلى عقد AST المستخدمة في الفحص والتقييم.
public final class PythonAstBuilder {

    private final String fileName;

    public PythonAstBuilder(String fileName) {
        this.fileName = fileName;
    }

    // يبني جذر شجرة بايثون مع تحويل التعليمات فقط؛ يتخلص من فواصل القواعد التي لا تحمل معنى دلاليا.
    public PythonProgram build(RuleNode module) {
        List<PythonStatement> statements = new ArrayList<>();
        for (RuleNode stmt : module.rules("statement")) statements.add(buildStatement(stmt));
        return new PythonProgram(module.position(), fileName, statements);
    }

    // ------------------------------------------------------------ statements

    // يختار الصنف المناسب لكل تعليمة؛ مثل تعريف دالة أو تعيين أو مسار أو حلقة.
    private PythonStatement buildStatement(RuleNode statement) {
        RuleNode inner = statement.rules().get(0);
        switch (inner.ruleName()) {
            case "function_definition": return buildFunction(inner, new ArrayList<>());
            case "if_statement": return buildIf(inner);
            case "for_statement": return buildFor(inner);
            case "decorated_definition": return buildDecorated(inner);
            case "simple_statement": return buildSmall(inner.rules().get(0));
            default: throw unexpected(inner);
        }
    }

    private List<PythonStatement> buildBlock(RuleNode block) {
        List<PythonStatement> out = new ArrayList<>();
        for (RuleNode stmt : block.rules("statement")) out.add(buildStatement(stmt));
        return out;
    }

    private PythonStatement buildSmall(RuleNode small) {
        switch (small.ruleName()) {
            case "import_statement": return buildImport(small);
            case "from_import_statement": return buildFromImport(small);
            case "return_statement": {
                RuleNode expr = small.rule("expression");
                return new ReturnStatement(small.position(), expr == null ? null : buildExpression(expr));
            }
            case "pass_statement": return new PassStatement(small.position());
            case "assignment_statement":
            case "augmented_assignment": {
                List<RuleNode> exprs = small.rules("expression");
                PythonExpression target = buildExpression(exprs.get(0));
                PythonExpression value = buildExpression(exprs.get(1));
                String op = small.firstToken().text();
                if (!(target instanceof Identifier || target instanceof AttributeAccess || target instanceof SubscriptAccess)) {
                    throw new CompilerException(ErrorCategory.SYNTAX, "SYN003", "InvalidAssignmentTarget",
                            "Cannot assign to this expression", target.position());
                }
                return new Assignment(small.position(), target, value, op);
            }
            case "expression_statement":
                return new ExpressionStatement(small.position(), buildExpression(small.rule("expression")));
            default: throw unexpected(small);
        }
    }

    private ImportStatement buildImport(RuleNode node) {
        List<ImportedName> names = new ArrayList<>();
        List<ParseTreeNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            ParseTreeNode c = children.get(i);
            if (!c.isToken() && c.name().equals("dotted_name")) {
                String dotted = String.join("", ((RuleNode) c).tokenTexts());
                String alias = null;
                if (i + 2 < children.size() && children.get(i + 1).isToken()
                        && ((TokenNode) children.get(i + 1)).text().equals("as")) {
                    alias = ((TokenNode) children.get(i + 2)).text();
                }
                names.add(new ImportedName(c.position(), dotted, alias));
            }
        }
        StringBuilder module = new StringBuilder();
        for (ImportedName n : names) { if (module.length() > 0) module.append(", "); module.append(n.name()); }
        return new ImportStatement(node.position(), module.toString(), false, names);
    }

    private ImportStatement buildFromImport(RuleNode node) {
        String module = String.join("", node.rule("dotted_name").tokenTexts());
        List<ImportedName> names = new ArrayList<>();
        for (RuleNode importName : node.rules("import_name")) {
            List<TokenNode> toks = importName.tokens();
            String name = toks.get(0).text();
            String alias = toks.size() == 3 ? toks.get(2).text() : null;
            names.add(new ImportedName(importName.position(), name, alias));
        }
        return new ImportStatement(node.position(), module, true, names);
    }

    // تُفحص زينة مسار التطبيق لكي تتحول تعريفات المسارات إلى عقد يمكن ربطها بقوالب العرض.
    private PythonStatement buildDecorated(RuleNode node) {
        List<PythonExpression> decorators = new ArrayList<>();
        for (RuleNode dec : node.rules("decorator")) decorators.add(buildExpression(dec.rule("expression")));
        RuleNode fnRule = node.rule("function_definition");

        RouteInfo route = null;
        List<PythonExpression> otherDecorators = new ArrayList<>();
        for (PythonExpression dec : decorators) {
            RouteInfo info = asRoute(dec);
            if (info != null) {
                if (route != null) {
                    throw new CompilerException(ErrorCategory.SYNTAX, "SYN002", "UnsupportedConstruct",
                            "Multiple @route decorators on one function are not supported", dec.position());
                }
                route = info;
            } else {
                otherDecorators.add(dec);
            }
        }
        FunctionDefinition fn = buildFunction(fnRule, otherDecorators);
        if (route == null) return fn;
        return new RouteDefinition(route.position, route.rule, route.methods, route.methodsPosition, fn, route.applicationName);
    }

    private static final class RouteInfo {
        String rule;
        List<String> methods = new ArrayList<>();
        SourcePosition position;
        SourcePosition methodsPosition;
        String applicationName;
    }

    /** Recognises {@code <anything>.route("/rule", methods=[...])}. */
    private RouteInfo asRoute(PythonExpression decorator) {
        if (!(decorator instanceof CallExpression)) return null;
        CallExpression call = (CallExpression) decorator;
        if (!(call.callee() instanceof AttributeAccess)) return null;
        if (!((AttributeAccess) call.callee()).attribute().equals("route")) return null;
        if (call.arguments().isEmpty() || !(call.arguments().get(0) instanceof Literal)
                || !((Literal) call.arguments().get(0)).isString()) {
            throw new CompilerException(ErrorCategory.SYNTAX, "SYN004", "InvalidRouteDecorator",
                    "@route requires a string literal URL rule as first argument", call.position());
        }
        RouteInfo info = new RouteInfo();
        info.position = call.position();
        info.rule = ((Literal) call.arguments().get(0)).stringValue();
        PythonExpression target = ((AttributeAccess) call.callee()).target();
        info.applicationName = target instanceof Identifier ? ((Identifier) target).name() : "<expression>";
        KeywordArgument methods = call.keyword("methods");
        if (methods == null) {
            info.methods.add("GET");
            info.methodsPosition = call.position();
        } else {
            info.methodsPosition = methods.position();
            if (!(methods.value() instanceof ListLiteral)) {
                throw new CompilerException(ErrorCategory.SYNTAX, "SYN004", "InvalidRouteDecorator",
                        "@route methods= must be a list literal of strings", methods.position());
            }
            for (PythonExpression e : ((ListLiteral) methods.value()).elements()) {
                if (!(e instanceof Literal) || !((Literal) e).isString()) {
                    throw new CompilerException(ErrorCategory.SYNTAX, "SYN004", "InvalidRouteDecorator",
                            "@route methods= must only contain string literals", e.position());
                }
                info.methods.add(((Literal) e).stringValue());
            }
        }
        return info;
    }

    private FunctionDefinition buildFunction(RuleNode node, List<PythonExpression> decorators) {
        String name = node.tokens().get(1).text();
        List<Parameter> params = new ArrayList<>();
        RuleNode paramList = node.rule("parameter_list");
        if (paramList != null) {
            for (RuleNode p : paramList.rules("parameter")) {
                RuleNode def = p.rule("expression");
                params.add(new Parameter(p.position(), p.firstToken().text(), def == null ? null : buildExpression(def)));
            }
        }
        return new FunctionDefinition(node.position(), name, params, buildBlock(node.rule("block")), decorators);
    }

    private IfStatement buildIf(RuleNode node) {
        List<ConditionalBranch> branches = new ArrayList<>();
        branches.add(new ConditionalBranch(node.position(), "if",
                buildExpression(node.rule("expression")), buildBlock(node.rule("block"))));
        for (RuleNode elif : node.rules("elif_clause")) {
            branches.add(new ConditionalBranch(elif.position(), "elif",
                    buildExpression(elif.rule("expression")), buildBlock(elif.rule("block"))));
        }
        RuleNode els = node.rule("else_clause");
        List<PythonStatement> elseBody = els == null ? new ArrayList<>() : buildBlock(els.rule("block"));
        return new IfStatement(node.position(), branches, elseBody);
    }

    private ForStatement buildFor(RuleNode node) {
        List<String> targets = new ArrayList<>();
        for (TokenNode t : node.rule("target_list").tokens()) {
            if (t.token().is(PythonTokenType.NAME)) targets.add(t.text());
        }
        return new ForStatement(node.position(), targets, buildExpression(node.rule("expression")),
                buildBlock(node.rule("block")));
    }

    // ----------------------------------------------------------- expressions

    public PythonExpression buildExpression(RuleNode expression) {
        return buildOrTest(expression.rule("or_test"));
    }

    private PythonExpression buildOrTest(RuleNode node) {
        List<RuleNode> operands = node.rules("and_test");
        PythonExpression result = buildAndTest(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = new LogicalExpression(node.position(), "or", result, buildAndTest(operands.get(i)));
        }
        return result;
    }

    private PythonExpression buildAndTest(RuleNode node) {
        List<RuleNode> operands = node.rules("not_test");
        PythonExpression result = buildNotTest(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = new LogicalExpression(node.position(), "and", result, buildNotTest(operands.get(i)));
        }
        return result;
    }

    private PythonExpression buildNotTest(RuleNode node) {
        if (node.hasToken("not")) {
            return new UnaryExpression(node.position(), "not", buildNotTest(node.rule("not_test")));
        }
        return buildComparison(node.rule("comparison"));
    }

    private PythonExpression buildComparison(RuleNode node) {
        List<RuleNode> operands = node.rules("arith_expr");
        List<RuleNode> operators = node.rules("comparison_operator");
        PythonExpression left = buildArith(operands.get(0));
        if (operators.isEmpty()) return left;
        PythonExpression result = null;
        for (int i = 0; i < operators.size(); i++) {
            String op = String.join(" ", operators.get(i).tokenTexts());
            PythonExpression right = buildArith(operands.get(i + 1));
            PythonExpression cmp = new ComparisonExpression(operators.get(i).position(), op, left, right);
            result = result == null ? cmp : new LogicalExpression(node.position(), "and", result, cmp);
            left = right;
        }
        return result;
    }

    private PythonExpression buildArith(RuleNode node) {
        return foldBinary(node, "term", this::buildTerm);
    }

    private PythonExpression buildTerm(RuleNode node) {
        return foldBinary(node, "factor", this::buildFactor);
    }

    private interface Builder { PythonExpression build(RuleNode n); }

    private PythonExpression foldBinary(RuleNode node, String operandRule, Builder builder) {
        PythonExpression result = null;
        String pendingOp = null;
        for (ParseTreeNode c : node.children()) {
            if (c.isToken()) {
                pendingOp = ((TokenNode) c).text();
            } else if (c.name().equals(operandRule)) {
                PythonExpression operand = builder.build((RuleNode) c);
                result = result == null ? operand : new BinaryExpression(c.position(), pendingOp, result, operand);
            }
        }
        return result;
    }

    private PythonExpression buildFactor(RuleNode node) {
        if (node.rule("factor") != null) {
            return new UnaryExpression(node.position(), node.firstToken().text(), buildFactor(node.rule("factor")));
        }
        return buildPrimary(node.rule("primary"));
    }

    private PythonExpression buildPrimary(RuleNode node) {
        PythonExpression result = buildAtom(node.rule("atom"));
        for (RuleNode trailer : node.rules("trailer")) {
            String first = trailer.firstToken().text();
            switch (first) {
                case ".":
                    result = new AttributeAccess(trailer.position(), result, trailer.tokens().get(1).text());
                    break;
                case "[":
                    result = new SubscriptAccess(trailer.position(), result, buildExpression(trailer.rule("expression")));
                    break;
                case "(":
                    result = buildCall(trailer, result);
                    break;
                default:
                    throw unexpected(trailer);
            }
        }
        return result;
    }

    private PythonExpression buildCall(RuleNode trailer, PythonExpression callee) {
        List<PythonExpression> positional = new ArrayList<>();
        List<KeywordArgument> keywords = new ArrayList<>();
        RuleNode args = trailer.rule("argument_list");
        if (args != null) {
            for (RuleNode arg : args.rules("argument")) {
                List<TokenNode> toks = arg.tokens();
                PythonExpression value = buildExpression(arg.rule("expression"));
                if (!toks.isEmpty()) {
                    keywords.add(new KeywordArgument(arg.position(), toks.get(0).text(), value));
                } else {
                    if (!keywords.isEmpty()) {
                        throw new CompilerException(ErrorCategory.SYNTAX, "SYN001", "PythonSyntaxError",
                                "Positional argument follows keyword argument", arg.position());
                    }
                    positional.add(value);
                }
            }
        }
        SourcePosition pos = callee.position();
        if (callee instanceof Identifier && ((Identifier) callee).name().equals("render_template")
                && !positional.isEmpty() && positional.get(0) instanceof Literal
                && ((Literal) positional.get(0)).isString()) {
            Literal name = (Literal) positional.get(0);
            return new RenderTemplateCall(pos, name.stringValue(), name.position(), keywords);
        }
        return new CallExpression(pos, callee, positional, keywords);
    }

    private PythonExpression buildAtom(RuleNode node) {
        RuleNode list = node.rule("list_display");
        if (list != null) {
            List<PythonExpression> elements = new ArrayList<>();
            for (RuleNode e : list.rules("expression")) elements.add(buildExpression(e));
            return new ListLiteral(list.position(), elements);
        }
        RuleNode dict = node.rule("dict_display");
        if (dict != null) {
            List<DictionaryEntry> entries = new ArrayList<>();
            for (RuleNode entry : dict.rules("dict_entry")) {
                List<RuleNode> kv = entry.rules("expression");
                entries.add(new DictionaryEntry(entry.position(), buildExpression(kv.get(0)), buildExpression(kv.get(1))));
            }
            return new DictionaryLiteral(dict.position(), entries);
        }
        RuleNode paren = node.rule("expression");
        if (paren != null) return buildExpression(paren);   // parentheses vanish from the AST

        List<TokenNode> tokens = node.tokens();
        TokenNode first = tokens.get(0);
        PythonTokenType type = (PythonTokenType) first.token().type();
        switch (type) {
            case NAME: return new Identifier(first.position(), first.text());
            case NUMBER: return numberLiteral(first);
            case STRING: {
                StringBuilder sb = new StringBuilder();
                for (TokenNode t : tokens) sb.append(t.text());
                return new Literal(first.position(), Literal.Kind.STRING, sb.toString());
            }
            case KEYWORD:
                switch (first.text()) {
                    case "True": return new Literal(first.position(), Literal.Kind.BOOLEAN, Boolean.TRUE);
                    case "False": return new Literal(first.position(), Literal.Kind.BOOLEAN, Boolean.FALSE);
                    case "None": return new Literal(first.position(), Literal.Kind.NONE, null);
                    default: throw unexpected(node);
                }
            default: throw unexpected(node);
        }
    }

    private Literal numberLiteral(TokenNode token) {
        String text = token.text();
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
            return new Literal(token.position(), Literal.Kind.FLOAT, Double.parseDouble(text));
        }
        try {
            return new Literal(token.position(), Literal.Kind.INTEGER, Long.parseLong(text));
        } catch (NumberFormatException e) {
            return new Literal(token.position(), Literal.Kind.FLOAT, Double.parseDouble(text));
        }
    }

    private CompilerException unexpected(ParseTreeNode node) {
        return new CompilerException(ErrorCategory.SYNTAX, "SYN001", "PythonSyntaxError",
                "Unexpected parse tree node '" + node.name() + "'", node.position());
    }
}
