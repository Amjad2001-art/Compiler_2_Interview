package fjc.output;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fjc.jinja.ast.JinjaAstBuilder;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.lexer.JinjaLexer;
import fjc.jinja.parser.JinjaParser;
import fjc.python.ast.PythonAstBuilder;
import fjc.python.ast.PythonProgram;
import fjc.python.lexer.PythonLexer;
import fjc.python.parser.PythonParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputWritersTest {

    static PythonProgram python(String src) {
        return new PythonAstBuilder("app.py").build(new PythonParser(new PythonLexer("app.py", src).tokenize()).parseModule());
    }

    static JinjaTemplate jinja(String src) {
        return new JinjaAstBuilder("t.jinja").build(new JinjaParser(new JinjaLexer("t.jinja", src).tokenize()).parseTemplate());
    }

    @Test
    void treePrinterPrintsEveryNodeRecursivelyWithIndentation() {
        PythonProgram p = python("products = [{\"name\": \"Phone\", \"price\": 300}]\n@app.route('/')\ndef index():\n    return render_template('index.jinja', products=products)\n");
        String tree = new AstTreePrinter(false).print(p);
        String[] lines = tree.split("\n");
        assertTrue(lines[0].startsWith("PythonProgram"));
        assertTrue(tree.contains("├── Assignment: products"));
        assertTrue(tree.contains("└── ListLiteral (1 elements)"));
        assertTrue(tree.contains("name = \"Phone\""));
        assertTrue(tree.contains("RouteDefinition: / [GET] -> index"));
        assertTrue(tree.contains("RenderTemplateCall: index.jinja [products]"));
        assertEquals(p.nodeCount(), lines.length, "one line per AST node");
        String ascii = new AstTreePrinter(true).print(p);
        assertTrue(ascii.contains("|-- ") || ascii.contains("`-- "));
        assertFalse(ascii.contains("├"));
    }

    @Test
    void jsonSerializerProducesStructuredNodesWithPositionsAndNoPunctuation() {
        PythonProgram p = python("x = (1 + 2)\n");
        JsonObject root = JsonParser.parseString(new AstJsonSerializer().toPrettyJson(p)).getAsJsonObject();
        assertEquals("PythonProgram", root.get("type").getAsString());
        assertEquals("app.py", root.get("file").getAsString());
        JsonObject assignment = root.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("Assignment", assignment.get("type").getAsString());
        assertEquals("x", assignment.get("target").getAsString());
        assertEquals(1, assignment.get("line").getAsInt());
        assertEquals(1, assignment.get("column").getAsInt());
        JsonObject binary = assignment.getAsJsonArray("children").get(1).getAsJsonObject();
        assertEquals("BinaryExpression", binary.get("type").getAsString());
        assertEquals("+", binary.get("operator").getAsString());
        Set<String> types = new HashSet<>();
        collectTypes(root, types);
        assertFalse(types.contains("OP") || types.contains("atom") || types.contains("expression"));
    }

    @Test
    void jinjaJsonKeepsHtmlStructureAndSemanticProperties() {
        JinjaTemplate t = jinja("<a href=\"{{ url_for('index') }}\">{{ p.name }}</a>");
        JsonObject root = new AstJsonSerializer().toJson(t);
        assertEquals("JinjaTemplate", root.get("type").getAsString());
        JsonObject a = root.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("HtmlElement", a.get("type").getAsString());
        assertEquals("a", a.get("tag").getAsString());
        JsonObject href = a.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("HtmlAttribute", href.get("type").getAsString());
        JsonObject urlFor = href.getAsJsonArray("children").get(0).getAsJsonObject().getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("UrlForExpression", urlFor.get("type").getAsString());
        assertEquals("index", urlFor.get("endpoint").getAsString());
    }

    private static void collectTypes(JsonObject o, Set<String> out) {
        out.add(o.get("type").getAsString());
        JsonArray children = o.getAsJsonArray("children");
        for (int i = 0; i < children.size(); i++) collectTypes(children.get(i).getAsJsonObject(), out);
    }
}
