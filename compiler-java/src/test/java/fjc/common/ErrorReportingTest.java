package fjc.common;

import fjc.TestPaths;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every diagnostic carries category, code, name, file, line and column in a stable format. */
class ErrorReportingTest {

    @TempDir Path temp;

    @Test
    void formatIsStable() {
        CompilerError e = CompilerError.error(ErrorCategory.PYTHON, "PY001", "UndefinedVariable", "Name 'x' is not defined",
                new SourcePosition("app.py", 12, 5));
        assertEquals("[PY001] ERROR app.py:12:5 UndefinedVariable: Name 'x' is not defined", e.format());
        ErrorCollector c = new ErrorCollector();
        c.add(e);
        c.add(e);
        assertEquals(1, c.size(), "duplicates are collapsed");
        c.warning(ErrorCategory.JINJA, "JJ014", "InvalidOutputType", "x", new SourcePosition("t.jinja", 1, 1));
        assertEquals(1, c.errorCount());
        assertEquals(1, c.warningCount());
        assertTrue(c.hasErrors());
    }

    @Test
    void positionsPointAtTheOffendingTokenInBothLanguages() throws Exception {
        Path p = temp.resolve("proj");
        Files.createDirectories(p.resolve("templates"));
        Files.writeString(p.resolve("app.py"),
                "from flask import Flask, render_template\n"
                        + "app = Flask(__name__)\n"
                        + "products = []\n"
                        + "\n"
                        + "@app.route('/')\n"
                        + "def index():\n"
                        + "    total = len(products) + undefined_name\n"
                        + "    return render_template('index.jinja', products=products, total=total)\n", StandardCharsets.UTF_8);
        Files.writeString(p.resolve("templates").resolve("index.jinja"),
                "<ul>\n  {% for p in products %}\n    <li>{{ p.name }} - {{ p.missing_key }}</li>\n  {% endfor %}\n</ul>\n"
                        + "<a href=\"{{ url_for('nowhere') }}\">{{ total }}</a>\n", StandardCharsets.UTF_8);
        CompilationResult r = TestPaths.compile(p, temp, true);

        CompilerError py = r.errors().withCode("PY001").get(0);
        assertEquals("app.py", py.fileName());
        assertEquals(7, py.line());
        assertEquals(29, py.column());

        CompilerError jj = r.errors().withCode("JJ004").get(0);
        assertEquals("index.jinja", jj.fileName());
        assertEquals(6, jj.line());
        assertEquals(21, jj.column(), "points at the endpoint string literal");

        String report = Files.readString(r.compilerOutputFiles().get("semantic_report.txt"));
        assertTrue(report.contains("[PY001] ERROR app.py:7:29 UndefinedVariable"));
        assertTrue(report.contains("[JJ004] ERROR index.jinja:6:21 UnknownEndpoint"));
    }
}
