package fjc.semantic;

import fjc.common.SourcePosition;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Collects the <em>free variables</em> of a template (names that must be
 * provided by the Flask route through render_template), respecting loop
 * scopes. Implemented as a {@link JinjaAstVisitor}.
 */
public final class TemplateRequirements implements JinjaAstVisitor<Void> {

    /** Names that Jinja/Flask provide without a context variable. */
    public static final Set<String> TEMPLATE_GLOBALS = Set.of(
            "url_for", "range", "request", "config", "get_flashed_messages", "dict", "namespace", "lipsum", "cycler", "joiner");

    private final Map<String, SourcePosition> free = new LinkedHashMap<>();
    private final Deque<Set<String>> scopes = new ArrayDeque<>();

    public static Map<String, SourcePosition> freeVariables(JinjaTemplate template) {
        TemplateRequirements r = new TemplateRequirements();
        template.accept(r);
        return r.free;
    }

    private boolean isLocal(String name) {
        for (Set<String> s : scopes) if (s.contains(name)) return true;
        return false;
    }

    private void visitAll(Iterable<? extends JinjaAstNode> nodes) {
        for (JinjaAstNode n : nodes) n.accept(this);
    }

    @Override public Void visitProject(JinjaProject node) { visitAll(node.templates()); return null; }
    @Override public Void visitTemplate(JinjaTemplate node) { visitAll(node.children()); return null; }
    @Override public Void visitText(TextNode node) { return null; }
    @Override public Void visitDoctype(HtmlDoctype node) { return null; }
    @Override public Void visitComment(HtmlComment node) { return null; }
    @Override public Void visitElement(HtmlElement node) { visitAll(node.attributes()); visitAll(node.body()); return null; }
    @Override public Void visitAttribute(HtmlAttribute node) { visitAll(node.valueParts()); return null; }
    @Override public Void visitOutput(OutputExpression node) { node.expression().accept(this); return null; }

    @Override public Void visitFor(ForStatement node) {
        node.iterable().accept(this);
        Set<String> scope = new HashSet<>(node.loopVariables());
        scope.add("loop");
        scopes.push(scope);
        visitAll(node.body());
        scopes.pop();
        return null;
    }

    @Override public Void visitIf(IfStatement node) { visitAll(node.branches()); visitAll(node.elseBody()); return null; }
    @Override public Void visitIfBranch(IfBranch node) { node.condition().accept(this); visitAll(node.body()); return null; }

    @Override public Void visitVariable(VariableReference node) {
        String name = node.name();
        if (!isLocal(name) && !TEMPLATE_GLOBALS.contains(name) && !free.containsKey(name)) free.put(name, node.position());
        return null;
    }

    @Override public Void visitPropertyAccess(PropertyAccess node) { node.target().accept(this); return null; }
    @Override public Void visitIndexAccess(IndexAccess node) { node.target().accept(this); node.index().accept(this); return null; }
    @Override public Void visitFilter(FilterExpression node) {
        node.target().accept(this);
        visitAll(node.arguments());
        visitAll(node.keywordArguments());
        return null;
    }
    @Override public Void visitTest(TestExpression node) {
        // "{% if x is defined %}" probes an optional variable: it is not a hard requirement of the template
        boolean probe = node.target() instanceof VariableReference
                && (node.testName().equals("defined") || node.testName().equals("undefined"));
        if (!probe) node.target().accept(this);
        visitAll(node.arguments());
        return null;
    }
    @Override public Void visitFunctionCall(FunctionCall node) {
        node.callee().accept(this);
        visitAll(node.arguments());
        visitAll(node.keywordArguments());
        return null;
    }
    @Override public Void visitUrlFor(UrlForExpression node) { visitAll(node.parameters()); return null; }
    @Override public Void visitLiteral(JinjaLiteral node) { return null; }
    @Override public Void visitListLiteral(JinjaListLiteral node) { visitAll(node.elements()); return null; }
    @Override public Void visitBinary(JinjaBinaryExpression node) { node.left().accept(this); node.right().accept(this); return null; }
    @Override public Void visitComparison(JinjaComparisonExpression node) { node.left().accept(this); node.right().accept(this); return null; }
    @Override public Void visitLogical(JinjaLogicalExpression node) { node.left().accept(this); node.right().accept(this); return null; }
    @Override public Void visitUnary(JinjaUnaryExpression node) { node.operand().accept(this); return null; }
    @Override public Void visitKeywordArgument(JinjaKeywordArgument node) { node.value().accept(this); return null; }
}
