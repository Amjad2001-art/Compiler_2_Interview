package fjc.python;

import fjc.ast.AstNode;
import fjc.parsetree.ParseTreeNode;
import fjc.parsetree.RuleNode;
import fjc.parsetree.TokenNode;
import fjc.python.ast.Assignment;
import fjc.python.ast.BinaryExpression;
import fjc.python.ast.DictionaryLiteral;
import fjc.python.ast.FunctionDefinition;
import fjc.python.ast.ListLiteral;
import fjc.python.ast.Literal;
import fjc.python.ast.PythonAstBuilder;
import fjc.python.ast.PythonProgram;
import fjc.python.ast.RenderTemplateCall;
import fjc.python.ast.ReturnStatement;
import fjc.python.ast.RouteDefinition;
import fjc.python.lexer.PythonLexer;
import fjc.python.parser.PythonParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonAstTest {

    static final String SAMPLE = "products = [\n    {\"id\": 1, \"name\": \"Phone\", \"price\": 300},\n"
            + "    {\"id\": 2, \"name\": \"Laptop\", \"price\": 800}\n]\n\n"
            + "@app.route(\"/\", methods=[\"GET\", \"POST\"])\ndef index():\n"
            + "    return render_template(\"index.jinja\", products=products)\n";

    static RuleNode parseTree(String src) {
        return new PythonParser(new PythonLexer("app.py", src).tokenize()).parseModule();
    }

    static PythonProgram ast(String src) {
        return new PythonAstBuilder("app.py").build(parseTree(src));
    }

    @Test
    void buildsSemanticNodesForDataAndRoutes() {
        PythonProgram program = ast(SAMPLE);
        assertEquals(2, program.statements().size());
        Assignment assignment = (Assignment) program.statements().get(0);
        assertEquals("products", assignment.targetName());
        ListLiteral list = (ListLiteral) assignment.value();
        assertEquals(2, list.elements().size());
        DictionaryLiteral first = (DictionaryLiteral) list.elements().get(0);
        assertEquals(List.of("id", "name", "price"), first.keyNames());
        assertEquals("Phone", ((Literal) first.entry("name").value()).stringValue());
        assertEquals(300L, ((Literal) first.entry("price").value()).value());

        RouteDefinition route = (RouteDefinition) program.statements().get(1);
        assertEquals("/", route.rule());
        assertEquals(List.of("GET", "POST"), route.methods());
        assertEquals("index", route.endpoint());
        assertEquals("app", route.applicationName());
        FunctionDefinition fn = route.function();
        ReturnStatement ret = (ReturnStatement) fn.body().get(0);
        RenderTemplateCall render = (RenderTemplateCall) ret.value();
        assertEquals("index.jinja", render.templateName());
        assertEquals(List.of("products"), render.contextNames());
        assertEquals(8, render.line());
    }

    @Test
    void parenthesesAndWrapperProductionsDoNotBecomeAstNodes() {
        PythonProgram program = ast("x = (1 + 2) * 3\n");
        Assignment a = (Assignment) program.statements().get(0);
        BinaryExpression mul = (BinaryExpression) a.value();
        assertEquals("*", mul.operator());
        BinaryExpression add = (BinaryExpression) mul.left();
        assertEquals("+", add.operator());
        assertEquals(3L, ((Literal) mul.right()).value());
        Set<String> names = nodeNames(program);
        for (String n : names) {
            assertFalse(n.contains("_"), "AST node names are semantic (" + n + "), not grammar rule names");
        }
    }

    @Test
    void astIsMateriallySmallerThanParseTreeAndContainsNoPunctuation() {
        RuleNode tree = parseTree(SAMPLE);
        PythonProgram program = ast(SAMPLE);
        int parseNodes = tree.nodeCount();
        int astNodes = program.nodeCount();
        assertTrue(astNodes * 3 < parseNodes, "AST (" + astNodes + ") must be much smaller than parse tree (" + parseNodes + ")");
        assertTrue(hasToken(tree, "(") && hasToken(tree, ",") && hasToken(tree, ":") && hasToken(tree, "="),
                "parse tree keeps punctuation tokens");
        Set<String> names = nodeNames(program);
        assertTrue(names.contains("PythonProgram") && names.contains("Assignment") && names.contains("ListLiteral")
                && names.contains("DictionaryLiteral") && names.contains("RouteDefinition") && names.contains("RenderTemplateCall"));
        for (String grammarOnly : List.of("or_test", "and_test", "not_test", "comparison", "arith_expr", "term", "factor", "primary", "atom", "trailer", "OP", "NEWLINE", "INDENT", "DEDENT")) {
            assertFalse(names.contains(grammarOnly), grammarOnly + " must not be an AST node");
        }
        assertNotEquals(tree.nodeCount(), program.nodeCount());
    }

    @Test
    void everyAstNodeStoresLineAndColumn() {
        PythonProgram program = ast(SAMPLE);
        checkPositions(program);
        Assignment a = (Assignment) program.statements().get(0);
        assertEquals(1, a.line());
        assertEquals(1, a.column());
        assertEquals("app.py", a.fileName());
    }

    private static void checkPositions(AstNode n) {
        assertTrue(n.line() >= 1, n.nodeName() + " has no line");
        assertTrue(n.column() >= 1, n.nodeName() + " has no column");
        for (AstNode c : n.children()) checkPositions(c);
    }

    static Set<String> nodeNames(AstNode root) {
        Set<String> out = new HashSet<>();
        collect(root, out);
        return out;
    }

    private static void collect(AstNode n, Set<String> out) {
        out.add(n.nodeName());
        for (AstNode c : n.children()) collect(c, out);
    }

    static boolean hasToken(ParseTreeNode n, String text) {
        if (n.isToken()) return ((TokenNode) n).text().equals(text);
        for (ParseTreeNode c : n.children()) if (hasToken(c, text)) return true;
        return false;
    }

    @Test
    void recognisesFlaskConstructsOnlyWhenTheShapeMatches() {
        PythonProgram program = ast("name = 'x.jinja'\ndef f():\n    return render_template(name)\n");
        FunctionDefinition fn = (FunctionDefinition) program.statements().get(1);
        ReturnStatement ret = (ReturnStatement) fn.body().get(0);
        assertInstanceOf(fjc.python.ast.CallExpression.class, ret.value());
        assertFalse(ret.value() instanceof RenderTemplateCall, "non-literal template names stay generic calls (checked as PY008)");
    }
}
