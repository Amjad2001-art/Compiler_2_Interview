package fjc.codegen;

import fjc.TestPaths;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: valid sample project → generated, connected pages + copied support files. */
class CodeGenerationTest {

    @TempDir static Path temp;
    static CompilationResult result;
    static Path out;

    @BeforeAll
    static void compile() {
        result = TestPaths.compile(TestPaths.sampleProject(), temp, false);
        out = temp.resolve("output");
    }

    static String read(String name) throws Exception {
        return Files.readString(out.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void compilationSucceedsAndProducesTheRequiredPages() {
        assertTrue(result.success(), result.errors().all().toString());
        assertEquals(CompilationResult.Stage.DONE, result.stageReached());
        for (String page : List.of("index.html", "add_product.html", "edit_product.html", "product_detail.html", "delete_product.html")) {
            assertTrue(Files.exists(out.resolve(page)), page + " must be generated");
        }
        assertEquals(5, result.generatedPages().size());
    }

    @Test
    void indexContainsTheProductsDefinedInThePythonSource() throws Exception {
        String index = read("index.html");
        for (String name : List.of("Wireless Headphones Pro", "Gaming Mechanical Keyboard", "4K Webcam Ultra", "Gaming Laptop", "Smart TV 55")) {
            assertTrue(index.contains(name), "index.html must list " + name);
        }
        assertTrue(index.contains("$129.99"), "\"%.2f\"|format applied to the Python price");
        assertTrue(index.contains("5 product(s) in 4 categories"), "|length works on Python lists");
        assertTrue(index.contains("<span class=\"chip chip--first\">Audio</span>"), "loop.first inside an attribute value");
        assertFalse(index.contains("{{") || index.contains("{%"), "no Jinja left in the output");
    }

    @Test
    void generatedPagesAreConnected() throws Exception {
        String index = read("index.html");
        assertTrue(index.contains("href=\"add_product.html\""));
        assertTrue(index.contains("href=\"product_detail.html?product_id=1\""));
        assertTrue(index.contains("href=\"edit_product.html?product_id=2\""));
        assertTrue(index.contains("href=\"delete_product.html?product_id=3\""));
        String detail = read("product_detail.html");
        assertTrue(detail.contains("href=\"index.html\""), "back link");
        assertTrue(detail.contains("href=\"edit_product.html?product_id=4\""));
        assertTrue(detail.contains("action=\"/delete/4\" method=\"POST\""), "delete form posts to the Flask runtime");
        assertTrue(detail.contains("data-confirm=\"Delete Gaming Laptop?\""), "delete confirmation");
        String edit = read("edit_product.html");
        assertTrue(edit.contains("action=\"/edit/1\" method=\"POST\""));
        assertTrue(edit.contains("href=\"product_detail.html?product_id=1\""), "cancel goes back to the detail page");
        assertTrue(edit.contains("<option value=\"Audio\" selected>"), "attribute-level if chose the product category");
        String add = read("add_product.html");
        assertTrue(add.contains("action=\"/add\" method=\"POST\""));
        assertTrue(add.contains("href=\"index.html\""));
        String delete = read("delete_product.html");
        assertTrue(delete.contains("action=\"/delete/5\" method=\"POST\""));
        assertTrue(delete.contains("Yes, delete it"));
    }

    @Test
    void parameterizedPagesContainOneInstancePerProduct() throws Exception {
        String detail = read("product_detail.html");
        assertEquals(5, count(detail, "class=\"page-instance\""));
        assertTrue(detail.contains("data-page-key=\"product_id=3\""));
        assertTrue(detail.contains("data-page-title=\"4K Webcam Ultra - ProductShop\""));
        assertEquals(1, count(detail, "<html lang=\"en\">"), "one document skeleton");
        assertTrue(detail.contains("Premium product: free shipping included."), "if/elif chose the branch for the expensive laptop");
        assertTrue(detail.contains("Budget pick: great value."));
        assertEquals(1, count(detail, "<script src=\"script.js\"></script>"), "shared script must execute once after all page instances");
        assertTrue(detail.lastIndexOf("<script src=\"script.js\"></script>") > detail.lastIndexOf("class=\"page-instance\""),
                "script must run after every product instance is in the DOM");
    }

    @Test
    void supportFilesAreCopiedUnchangedAndNotReportedAsGenerated() throws Exception {
        for (String f : List.of("app.py", "style.css", "script.js")) assertTrue(Files.exists(out.resolve(f)), f);
        assertEquals(Files.readString(TestPaths.sampleProject().resolve("static").resolve("style.css")), read("style.css"));
        assertEquals(Files.readString(TestPaths.sampleProject().resolve("app.py")), read("app.py"));
        assertEquals(3, result.copiedFiles().size());
        assertTrue(result.generatedPages().stream().noneMatch(p -> p.fileName().endsWith(".css") || p.fileName().endsWith(".py")));
        assertTrue(result.log().contains("Copied support file (unchanged): style.css"));
        assertTrue(result.log().contains("Copied support file (analysed input, not generated): app.py"));
    }

    @Test
    void outputIsHtmlEscaped() throws Exception {
        Path p = temp.resolve("esc");
        Files.createDirectories(p.resolve("templates"));
        Files.writeString(p.resolve("app.py"), "from flask import Flask, render_template\napp = Flask(__name__)\nitems = [{\"id\": 1, \"name\": \"<b>Bold & Co</b>\"}]\n"
                + "@app.route('/')\ndef index():\n    return render_template('index.jinja', items=items)\n");
        Files.writeString(p.resolve("templates").resolve("index.jinja"), "{% for i in items %}<p title=\"{{ i.name }}\">{{ i.name }} {{ i.name|safe }}</p>{% endfor %}");
        CompilationResult r = TestPaths.compile(p, temp.resolve("esc_out"), false);
        assertTrue(r.success(), r.errors().all().toString());
        String html = Files.readString(temp.resolve("esc_out").resolve("output").resolve("index.html"));
        assertTrue(html.contains("title=\"&lt;b&gt;Bold &amp; Co&lt;/b&gt;\""));
        assertTrue(html.contains(">&lt;b&gt;Bold &amp; Co&lt;/b&gt; <b>Bold & Co</b></p>"));
    }

    @Test
    void compilerOutputFilesExist() {
        for (String f : List.of("ast_python.json", "ast_jinja.json", "semantic_report.txt", "generation_log.txt")) {
            assertTrue(Files.exists(temp.resolve("compiler_output").resolve(f)), f);
        }
        for (String f : List.of("symbol_table.txt", "ast_css.json", "context_data.json", "ast_python_tree.txt", "parse_tree_python.txt")) {
            assertTrue(Files.exists(temp.resolve("compiler_details").resolve(f)), f);
            assertFalse(Files.exists(temp.resolve("compiler_output").resolve(f)), "Must not appear in the required four-file directory: " + f);
        }
        assertTrue(result.log().contains("Compilation finished: SUCCESS"));
        assertTrue(result.log().contains("Generated index.html"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        int i = 0;
        while ((i = text.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
