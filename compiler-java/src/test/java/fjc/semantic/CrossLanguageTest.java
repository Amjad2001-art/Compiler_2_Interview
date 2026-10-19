package fjc.semantic;

import fjc.TestPaths;
import fjc.common.CompilerError;
import fjc.common.ErrorCategory;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossLanguageTest {

    @TempDir Path temp;

    private Path project(String app, String template) throws Exception {
        Path p = temp.resolve("proj");
        Files.createDirectories(p.resolve("templates"));
        Files.writeString(p.resolve("app.py"), app, StandardCharsets.UTF_8);
        Files.writeString(p.resolve("templates").resolve("index.jinja"), template, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void missingContextVariableIsReportedOnThePythonSideWhenPythonKnowsTheName() throws Exception {
        Path p = project("from flask import Flask, render_template\napp = Flask(__name__)\nproducts = [{\"id\": 1, \"name\": \"A\", \"price\": 1}]\n"
                + "@app.route('/')\ndef index():\n    return render_template('index.jinja')\n",
                "<ul>{% for p in products %}<li>{{ p.name }}</li>{% endfor %}</ul>");
        CompilationResult r = TestPaths.compile(p, temp.resolve("o1"), true);
        assertTrue(r.errors().hasCode("PY004"), r.errors().all().toString());
        CompilerError e = r.errors().withCode("PY004").get(0);
        assertEquals("app.py", e.fileName());
        assertEquals(6, e.line());
        assertFalse(r.errors().hasCode("JJ001") || r.errors().hasCode("JJ003"), "not duplicated on the Jinja side");
    }

    @Test
    void unknownNameIsReportedOnTheJinjaSideWhenPythonDoesNotKnowIt() throws Exception {
        Path p = project("from flask import Flask, render_template\napp = Flask(__name__)\nproducts = [{\"id\": 1, \"name\": \"A\", \"price\": 1}]\n"
                + "@app.route('/')\ndef index():\n    return render_template('index.jinja', products=products)\n",
                "<p>{{ shop_title }}</p>");
        CompilationResult r = TestPaths.compile(p, temp.resolve("o2"), true);
        assertTrue(r.errors().hasCode("JJ001"));
        assertFalse(r.errors().hasCode("PY004"));
        assertEquals("index.jinja", r.errors().withCode("JJ001").get(0).fileName());
    }

    @Test
    void shapesFlowFromPythonToJinja() throws Exception {
        Path p = project("from flask import Flask, render_template\napp = Flask(__name__)\nproducts = [{\"id\": 1, \"name\": \"A\", \"price\": 1}]\n"
                + "@app.route('/')\ndef index():\n    return render_template('index.jinja', products=products, title='Shop')\n",
                "<h1>{{ title }}</h1>{% for p in products %}{{ p.name }} {{ p.colour }}{% endfor %}{% for c in title %}x{% endfor %}");
        CompilationResult r = TestPaths.compile(p, temp.resolve("o3"), true);
        assertTrue(r.errors().hasCode("JJ002"), "unknown key colour on the dict shape inferred from Python");
        assertTrue(r.errors().withCode("JJ002").get(0).message().contains("[id, name, price]"));
        assertTrue(r.errors().hasCode("JJ003"), "iterating a str provided by Python");
    }

    @Test
    void formMethodMismatchAndUnusedContextAreCrossLanguageDiagnostics() throws Exception {
        Path p = project("from flask import Flask, render_template\napp = Flask(__name__)\nproducts = []\n"
                + "@app.route('/')\ndef index():\n    return render_template('index.jinja', products=products, extra=1)\n",
                "<form method=\"POST\" action=\"{{ url_for('index') }}\"><button>x</button></form>{{ products|length }}");
        CompilationResult r = TestPaths.compile(p, temp.resolve("o4"), true);
        assertTrue(r.errors().hasCode("XL001"));
        assertEquals(ErrorCategory.CROSS, r.errors().withCode("XL001").get(0).category());
        assertTrue(r.errors().hasCode("XL002"));
        assertFalse(r.errors().withCode("XL002").get(0).isError(), "unused context is a warning");
    }
}
