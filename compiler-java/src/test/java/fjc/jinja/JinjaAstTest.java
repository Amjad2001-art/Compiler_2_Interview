package fjc.jinja;

import fjc.ast.AstNode;
import fjc.jinja.ast.FilterExpression;
import fjc.jinja.ast.ForStatement;
import fjc.jinja.ast.HtmlAttribute;
import fjc.jinja.ast.HtmlElement;
import fjc.jinja.ast.IfStatement;
import fjc.jinja.ast.JinjaAstBuilder;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.ast.OutputExpression;
import fjc.jinja.ast.PropertyAccess;
import fjc.jinja.ast.TextNode;
import fjc.jinja.ast.UrlForExpression;
import fjc.jinja.lexer.JinjaLexer;
import fjc.jinja.parser.JinjaParser;
import fjc.parsetree.RuleNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JinjaAstTest {

    static final String SAMPLE = "<!DOCTYPE html>\n<ul class=\"list\">\n{% for product in products %}\n"
            + "  <li id=\"p-{{ product.id }}\"><a href=\"{{ url_for('product_detail', product_id=product.id) }}\">{{ product.name|upper }}</a>"
            + "{% if product.price > 100 %} (premium){% else %} (budget){% endif %}</li>\n{% endfor %}\n</ul>\n";

    static RuleNode parseTree(String src) {
        return new JinjaParser(new JinjaLexer("index.jinja", src).tokenize()).parseTemplate();
    }

    static JinjaTemplate ast(String src) {
        return new JinjaAstBuilder("index.jinja").build(parseTree(src));
    }

    @Test
    void buildsHtmlStructureWithJinjaNodes() {
        JinjaTemplate t = ast(SAMPLE);
        assertEquals("index.jinja", t.name());
        HtmlElement ul = (HtmlElement) t.children().get(2);
        assertEquals("ul", ul.tagName());
        assertEquals("list", ul.attribute("class").staticValue());
        ForStatement loop = (ForStatement) ul.body().get(1);
        assertEquals(List.of("product"), loop.loopVariables());
        assertEquals(3, loop.line());
        HtmlElement li = (HtmlElement) loop.body().get(1);
        assertEquals("li", li.tagName());
        HtmlAttribute id = li.attribute("id");
        assertNull(id.staticValue(), "dynamic attribute value");
        assertInstanceOf(TextNode.class, id.valueParts().get(0));
        assertInstanceOf(OutputExpression.class, id.valueParts().get(1));
        HtmlElement a = (HtmlElement) li.body().get(0);
        OutputExpression href = (OutputExpression) a.attribute("href").valueParts().get(0);
        UrlForExpression url = (UrlForExpression) href.expression();
        assertEquals("product_detail", url.endpoint());
        assertEquals(List.of("product_id"), url.parameterNames());
        FilterExpression filter = (FilterExpression) ((OutputExpression) a.body().get(0)).expression();
        assertEquals("upper", filter.filterName());
        assertEquals("product.name", ((PropertyAccess) filter.target()).dottedName());
        IfStatement ifs = (IfStatement) li.body().get(1);
        assertEquals(1, ifs.branches().size());
        assertTrue(ifs.hasElse());
    }

    @Test
    void attributeLevelIfBecomesIfStatementInsideElementAttributes() {
        JinjaTemplate t = ast("<option value=\"{{ c }}\" {% if c == sel %}selected{% endif %}>{{ c }}</option>");
        HtmlElement option = (HtmlElement) t.children().get(0);
        assertEquals(2, option.attributes().size());
        assertInstanceOf(IfStatement.class, option.attributes().get(1));
        IfStatement ifs = (IfStatement) option.attributes().get(1);
        assertInstanceOf(HtmlAttribute.class, ifs.branches().get(0).body().get(0));
    }

    @Test
    void astIsMuchSmallerThanParseTreeAndHasNoDelimiters() {
        RuleNode tree = parseTree(SAMPLE);
        JinjaTemplate t = ast(SAMPLE);
        assertTrue(t.nodeCount() * 3 < tree.nodeCount(), "AST " + t.nodeCount() + " vs parse tree " + tree.nodeCount());
        assertTrue(PythonAstTestHelper.hasToken(tree, "{{") && PythonAstTestHelper.hasToken(tree, "%}") && PythonAstTestHelper.hasToken(tree, "|"));
        Set<String> names = new HashSet<>();
        collect(t, names);
        for (String grammarOnly : List.of("node", "start_tag", "end_tag", "attr_value", "attr_part", "output", "for_block", "if_block",
                "expression", "postfix", "trailer", "atom", "VAR_BEGIN", "VAR_END", "BLOCK_BEGIN", "BLOCK_END", "TAG_CLOSE")) {
            assertFalse(names.contains(grammarOnly), grammarOnly + " must not appear in the AST");
        }
        assertTrue(names.containsAll(List.of("JinjaTemplate", "HtmlDoctype", "HtmlElement", "HtmlAttribute", "ForStatement",
                "IfStatement", "OutputExpression", "UrlForExpression", "FilterExpression", "PropertyAccess", "VariableReference", "TextNode")));
    }

    @Test
    void everyNodeCarriesFileLineAndColumn() {
        JinjaTemplate t = ast(SAMPLE);
        check(t);
        HtmlElement ul = (HtmlElement) t.children().get(2);
        assertEquals(2, ul.line());
        assertEquals("index.jinja", ul.fileName());
    }

    private static void check(AstNode n) {
        assertTrue(n.line() >= 1 && n.column() >= 1, n.nodeName() + " lacks a position");
        for (AstNode c : n.children()) check(c);
    }

    private static void collect(AstNode n, Set<String> out) {
        out.add(n.nodeName());
        for (AstNode c : n.children()) collect(c, out);
    }

    /** Small helper shared with the Python tests. */
    static final class PythonAstTestHelper {
        static boolean hasToken(fjc.parsetree.ParseTreeNode n, String text) {
            if (n.isToken()) return ((fjc.parsetree.TokenNode) n).text().equals(text);
            for (fjc.parsetree.ParseTreeNode c : n.children()) if (hasToken(c, text)) return true;
            return false;
        }
    }
}
