package fjc.jinja.ast;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.jinja.lexer.JinjaTokenType;
import fjc.jinja.parser.JinjaParser;
import fjc.parsetree.ParseTreeNode;
import fjc.parsetree.RuleNode;
import fjc.parsetree.TokenNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the compressed Jinja AST from the concrete parse tree.
 *
 * <p>Dropped on the way: '{{', '}}', '{%', '%}', quotes, '=', '|', '(', ')',
 * ',', '.', the 'for'/'in'/'endfor'/'if'/'endif' keywords, end tags and all
 * single-child wrapper productions (node, attribute_item, expression, or_test,
 * ... postfix, atom).
 *
 * <p>Recognised: HTML elements become {@link HtmlElement} trees with
 * {@link HtmlAttribute} children, {@code url_for('endpoint', ...)} becomes a
 * {@link UrlForExpression}, {@code x|f(...)} becomes a {@link FilterExpression}
 * and {@code x is test} becomes a {@link TestExpression}.
 */
// بناء شجرة Jinja المجردة بما يشمل عناصر HTML والمتغيرات والحلقات والشروط.
public final class JinjaAstBuilder {

    private final String templateName;

    public JinjaAstBuilder(String templateName) {
        this.templateName = templateName;
    }

    // يجمع أبناء قالب جينجا في شجرة مجردة؛ القالب هو الجذر الذي تعالجه مرحلة التوليد لاحقا.
    public JinjaTemplate build(RuleNode template) {
        List<JinjaAstNode> nodes = new ArrayList<>();
        for (RuleNode n : template.rules("node")) nodes.add(buildBodyItem(n));
        return new JinjaTemplate(template.position(), templateName, nodes);
    }

    // ---------------------------------------------------------------- nodes

    /** Converts a node / attribute_item / attr_part wrapper into an AST node. */
    // يحوّل عناصر القواعد إلى عقد فعلية مثل عنصر واجهة أو نص أو شرط أو تعبير طباعة.
    private JinjaAstNode buildBodyItem(RuleNode wrapper) {
        if (!wrapper.tokens().isEmpty() && wrapper.rules().isEmpty()) {
            TokenNode t = wrapper.tokens().get(0);          // attr_part with ATTR_TEXT
            return new TextNode(t.position(), t.text());
        }
        RuleNode inner = wrapper.rules().get(0);
        switch (inner.ruleName()) {
            case "text": return new TextNode(inner.position(), inner.firstToken().text());
            case "doctype": return new HtmlDoctype(inner.position(), inner.firstToken().text());
            case "comment": return new HtmlComment(inner.position(), inner.firstToken().text());
            case "element": return buildElement(inner);
            case "output": return buildOutput(inner);
            case "for_block": case "attr_for_block": case "value_for_block": return buildFor(inner);
            case "if_block": case "attr_if_block": case "value_if_block": return buildIf(inner);
            case "attribute": return buildAttribute(inner);
            default: throw unexpected(inner);
        }
    }

    private List<JinjaAstNode> buildBody(RuleNode parent) {
        List<JinjaAstNode> out = new ArrayList<>();
        for (RuleNode r : parent.rules()) {
            switch (r.ruleName()) {
                case "node": case "attribute_item": case "attr_part":
                    out.add(buildBodyItem(r));
                    break;
                default:
                    break;   // tags (for_tag, endfor_tag, ...) are grammar detail, not AST
            }
        }
        return out;
    }

    private HtmlElement buildElement(RuleNode element) {
        RuleNode startTag = element.rule("start_tag");
        TokenNode open = startTag.firstToken();
        String tag = open.text();
        List<JinjaAstNode> attributes = new ArrayList<>();
        for (RuleNode item : startTag.rules("attribute_item")) attributes.add(buildBodyItem(item));
        boolean selfClosed = false;
        for (TokenNode t : startTag.tokens()) if (t.token().is(JinjaTokenType.TAG_SELF_CLOSE)) selfClosed = true;
        List<JinjaAstNode> children = new ArrayList<>();
        for (RuleNode n : element.rules("node")) children.add(buildBodyItem(n));
        boolean isVoid = JinjaParser.VOID_ELEMENTS.contains(tag) || (selfClosed && element.rule("end_tag") == null);
        return new HtmlElement(open.position(), tag, attributes, children, isVoid);
    }

    private HtmlAttribute buildAttribute(RuleNode attribute) {
        TokenNode nameTok = attribute.firstToken();
        RuleNode value = attribute.rule("attr_value");
        if (value == null) return new HtmlAttribute(nameTok.position(), nameTok.text(), null, false);
        TokenNode first = value.firstToken();
        if (first != null && first.token().is(JinjaTokenType.ATTR_UNQUOTED)) {
            List<JinjaAstNode> parts = new ArrayList<>();
            parts.add(new TextNode(first.position(), first.text()));
            return new HtmlAttribute(nameTok.position(), nameTok.text(), parts, false);
        }
        RuleNode directOutput = value.rule("output");
        if (directOutput != null && first == null) {
            List<JinjaAstNode> parts = new ArrayList<>();
            parts.add(buildOutput(directOutput));
            return new HtmlAttribute(nameTok.position(), nameTok.text(), parts, false);
        }
        List<JinjaAstNode> parts = new ArrayList<>();
        for (RuleNode part : value.rules("attr_part")) parts.add(buildBodyItem(part));
        return new HtmlAttribute(nameTok.position(), nameTok.text(), parts, true);
    }

    private OutputExpression buildOutput(RuleNode output) {
        return new OutputExpression(output.position(), buildExpression(output.rule("expression")));
    }

    // عقدة الحلقة تحفظ متغيرات التكرار والتعبير القابل للتكرار وجسم الحلقة الذي سيعالجه الزائر.
    private ForStatement buildFor(RuleNode block) {
        RuleNode forTag = block.rule("for_tag");
        List<String> targets = new ArrayList<>();
        for (TokenNode t : forTag.rule("loop_targets").tokens()) {
            if (t.token().is(JinjaTokenType.NAME)) targets.add(t.text());
        }
        JinjaExpression iterable = buildExpression(forTag.rule("expression"));
        return new ForStatement(block.position(), targets, iterable, buildBody(block));
    }

    private IfStatement buildIf(RuleNode block) {
        List<IfBranch> branches = new ArrayList<>();
        RuleNode ifTag = block.rule("if_tag");
        branches.add(new IfBranch(block.position(), "if", buildExpression(ifTag.rule("expression")),
                buildBody(block.rule("if_body"))));
        for (RuleNode elif : block.rules("elif_clause")) {
            branches.add(new IfBranch(elif.position(), "elif", buildExpression(elif.rule("expression")), buildBody(elif)));
        }
        RuleNode els = block.rule("else_clause");
        List<JinjaAstNode> elseBody = els == null ? new ArrayList<>() : buildBody(els);
        return new IfStatement(block.position(), branches, elseBody);
    }

    // ---------------------------------------------------------- expressions

    public JinjaExpression buildExpression(RuleNode expression) {
        return buildOrTest(expression.rule("or_test"));
    }

    private JinjaExpression buildOrTest(RuleNode node) {
        List<RuleNode> operands = node.rules("and_test");
        JinjaExpression result = buildAndTest(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = new JinjaLogicalExpression(node.position(), "or", result, buildAndTest(operands.get(i)));
        }
        return result;
    }

    private JinjaExpression buildAndTest(RuleNode node) {
        List<RuleNode> operands = node.rules("not_test");
        JinjaExpression result = buildNotTest(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = new JinjaLogicalExpression(node.position(), "and", result, buildNotTest(operands.get(i)));
        }
        return result;
    }

    private JinjaExpression buildNotTest(RuleNode node) {
        if (node.hasToken("not")) {
            return new JinjaUnaryExpression(node.position(), "not", buildNotTest(node.rule("not_test")));
        }
        return buildComparison(node.rule("comparison"));
    }

    private JinjaExpression buildComparison(RuleNode node) {
        List<RuleNode> parts = node.rules();
        JinjaExpression left = buildAdditive(parts.get(0));
        JinjaExpression result = null;
        String pendingOp = null;
        for (int i = 1; i < parts.size(); i++) {
            RuleNode part = parts.get(i);
            if (part.ruleName().equals("test")) {
                left = buildTest(part, left);
                if (result != null) result = replaceLastOperand(result, left);
                continue;
            }
            if (part.ruleName().equals("comparison_operator")) {
                pendingOp = String.join(" ", part.tokenTexts());
                continue;
            }
            JinjaExpression right = buildAdditive(part);
            JinjaExpression cmp = new JinjaComparisonExpression(part.position(), pendingOp, left, right);
            result = result == null ? cmp : new JinjaLogicalExpression(node.position(), "and", result, cmp);
            left = right;
        }
        return result == null ? left : result;
    }

    /** {@code a == b is defined}: the test applies to the whole comparison (rare; keep the last one). */
    private JinjaExpression replaceLastOperand(JinjaExpression result, JinjaExpression tested) {
        return tested;
    }

    private JinjaExpression buildTest(RuleNode test, JinjaExpression target) {
        boolean negated = false;
        String name = null;
        for (TokenNode t : test.tokens()) {
            if (t.text().equals("not")) negated = true;
            else if (t.token().is(JinjaTokenType.NAME)) { name = t.text(); break; }
        }
        List<JinjaExpression> args = new ArrayList<>();
        RuleNode callArgs = test.rule("call_args");
        if (callArgs != null) {
            for (RuleNode arg : callArgs.rules("call_arg")) args.add(buildExpression(arg.rule("expression")));
        } else if (test.rule("atom") != null) {
            args.add(buildAtom(test.rule("atom")));
        }
        return new TestExpression(test.position(), target, name, negated, args);
    }

    private JinjaExpression buildAdditive(RuleNode node) {
        return foldBinary(node, "multiplicative", this::buildMultiplicative);
    }

    private JinjaExpression buildMultiplicative(RuleNode node) {
        return foldBinary(node, "unary", this::buildUnary);
    }

    private interface Builder { JinjaExpression build(RuleNode n); }

    private JinjaExpression foldBinary(RuleNode node, String operandRule, Builder builder) {
        JinjaExpression result = null;
        String pendingOp = null;
        for (ParseTreeNode c : node.children()) {
            if (c.isToken()) {
                pendingOp = ((TokenNode) c).text();
            } else if (c.name().equals(operandRule)) {
                JinjaExpression operand = builder.build((RuleNode) c);
                result = result == null ? operand : new JinjaBinaryExpression(c.position(), pendingOp, result, operand);
            }
        }
        return result;
    }

    private JinjaExpression buildUnary(RuleNode node) {
        if (node.rule("unary") != null) {
            return new JinjaUnaryExpression(node.position(), node.firstToken().text(), buildUnary(node.rule("unary")));
        }
        return buildPostfix(node.rule("postfix"));
    }

    private JinjaExpression buildPostfix(RuleNode node) {
        JinjaExpression result = buildAtom(node.rule("atom"));
        for (RuleNode trailer : node.rules("trailer")) {
            RuleNode filter = trailer.rule("filter");
            if (filter != null) {
                String name = filter.tokens().get(1).text();
                List<JinjaExpression> args = new ArrayList<>();
                List<JinjaKeywordArgument> kwargs = new ArrayList<>();
                collectArgs(filter.rule("call_args"), args, kwargs);
                result = new FilterExpression(filter.position(), result, name, args, kwargs);
                continue;
            }
            String first = trailer.firstToken().text();
            switch (first) {
                case ".":
                    result = new PropertyAccess(trailer.position(), result, trailer.tokens().get(1).text());
                    break;
                case "[":
                    result = new IndexAccess(trailer.position(), result, buildExpression(trailer.rule("expression")));
                    break;
                case "(": {
                    List<JinjaExpression> args = new ArrayList<>();
                    List<JinjaKeywordArgument> kwargs = new ArrayList<>();
                    collectArgs(trailer.rule("call_args"), args, kwargs);
                    if (result instanceof VariableReference && ((VariableReference) result).name().equals("url_for")
                            && !args.isEmpty() && args.get(0) instanceof JinjaLiteral && ((JinjaLiteral) args.get(0)).isString()) {
                        JinjaLiteral endpoint = (JinjaLiteral) args.get(0);
                        result = new UrlForExpression(result.position(), endpoint.stringValue(), endpoint.position(), kwargs);
                    } else {
                        result = new FunctionCall(result.position(), result, args, kwargs);
                    }
                    break;
                }
                default:
                    throw unexpected(trailer);
            }
        }
        return result;
    }

    private void collectArgs(RuleNode callArgs, List<JinjaExpression> args, List<JinjaKeywordArgument> kwargs) {
        if (callArgs == null) return;
        for (RuleNode arg : callArgs.rules("call_arg")) {
            JinjaExpression value = buildExpression(arg.rule("expression"));
            List<TokenNode> toks = arg.tokens();
            if (!toks.isEmpty()) kwargs.add(new JinjaKeywordArgument(arg.position(), toks.get(0).text(), value));
            else args.add(value);
        }
    }

    private JinjaExpression buildAtom(RuleNode atom) {
        RuleNode list = atom.rule("list_literal");
        if (list != null) {
            List<JinjaExpression> elements = new ArrayList<>();
            for (RuleNode e : list.rules("expression")) elements.add(buildExpression(e));
            return new JinjaListLiteral(list.position(), elements);
        }
        RuleNode paren = atom.rule("expression");
        if (paren != null) return buildExpression(paren);
        TokenNode t = atom.firstToken();
        switch ((JinjaTokenType) t.token().type()) {
            case NAME: return new VariableReference(t.position(), t.text());
            case STRING: return new JinjaLiteral(t.position(), JinjaLiteral.Kind.STRING, t.text());
            case NUMBER:
                if (t.text().contains(".")) return new JinjaLiteral(t.position(), JinjaLiteral.Kind.FLOAT, Double.parseDouble(t.text()));
                return new JinjaLiteral(t.position(), JinjaLiteral.Kind.INTEGER, Long.parseLong(t.text()));
            case KEYWORD:
                switch (t.text().toLowerCase()) {
                    case "true": return new JinjaLiteral(t.position(), JinjaLiteral.Kind.BOOLEAN, Boolean.TRUE);
                    case "false": return new JinjaLiteral(t.position(), JinjaLiteral.Kind.BOOLEAN, Boolean.FALSE);
                    case "none": return new JinjaLiteral(t.position(), JinjaLiteral.Kind.NONE, null);
                    default: throw unexpected(atom);
                }
            default: throw unexpected(atom);
        }
    }

    private CompilerException unexpected(ParseTreeNode node) {
        return new CompilerException(ErrorCategory.SYNTAX, "SYN101", "JinjaSyntaxError",
                "Unexpected parse tree node '" + node.name() + "'", node.position());
    }
}
