package fjc.codegen;

import fjc.TestPaths;
import fjc.common.ErrorCategory;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CODE GENERATION MUST STOP IF ANY ERROR EXISTS. */
class GenerationBlockingTest {

    @TempDir Path temp;

    @Test
    void invalidProjectDetectsPythonAndJinjaErrorsAndNeverTouchesTheOutput() throws Exception {
        Path out = temp.resolve("output");
        Files.createDirectories(out);
        Path sentinel = out.resolve("index.html");
        Files.writeString(sentinel, "SENTINEL - must not be overwritten", StandardCharsets.UTF_8);

        CompilationResult r = TestPaths.compile(TestPaths.invalidProject(), temp, false);

        assertFalse(r.success());
        assertEquals(CompilationResult.Stage.SEMANTIC, r.stageReached());
        assertEquals("semantic errors", r.failureReason());
        for (String code : new String[]{"PY001", "PY002", "PY003", "PY004", "PY005", "JJ001", "JJ003", "JJ004", "JJ005", "JJ006"}) {
            assertTrue(r.errors().hasCode(code), code + " expected, got " + r.errors().all());
        }
        assertEquals(5, r.errors().byCategory(ErrorCategory.PYTHON).size(), "five Python errors");
        assertEquals(5, r.errors().byCategory(ErrorCategory.JINJA).size(), "five Jinja errors");

        assertEquals("SENTINEL - must not be overwritten", Files.readString(sentinel));
        try (Stream<Path> files = Files.list(out)) {
            assertEquals(1, files.count(), "no new files in output/");
        }
        assertNull(r.contexts(), "context preparation never ran");
        assertTrue(r.generatedPages().isEmpty());
        assertTrue(r.copiedFiles().isEmpty());

        String log = Files.readString(temp.resolve("compiler_output").resolve("generation_log.txt"));
        assertTrue(log.contains("CODE GENERATION ABORTED: semantic errors"));
        assertTrue(log.contains("No files were generated or overwritten"));
        assertTrue(log.contains("Compilation finished: FAILURE"));
        assertFalse(log.contains("Code generation started"));
        String report = Files.readString(temp.resolve("compiler_output").resolve("semantic_report.txt"));
        assertTrue(report.contains("SEMANTIC ANALYSIS FAILED - code generation blocked"));
        assertTrue(report.contains("[PY001] ERROR app.py:"));
        assertTrue(report.contains("[JJ004] ERROR product_detail.jinja:"));
    }

    @Test
    void syntaxErrorsAlsoBlockGeneration() throws Exception {
        Path p = temp.resolve("syntax");
        Files.createDirectories(p.resolve("templates"));
        Files.writeString(p.resolve("app.py"), "from flask import Flask, render_template\napp = Flask(__name__)\n@app.route('/')\ndef index()\n    return render_template('index.jinja')\n");
        Files.writeString(p.resolve("templates").resolve("index.jinja"), "<p>{{ x </p>");
        CompilationResult r = TestPaths.compile(p, temp.resolve("syntax_out"), false);
        assertFalse(r.success());
        assertEquals(CompilationResult.Stage.LEXICAL_SYNTAX, r.stageReached());
        assertTrue(r.errors().hasCode("SYN001"));
        assertTrue(r.errors().hasCode("LEX101"), "both files are reported: " + r.errors().all());
        assertFalse(Files.exists(temp.resolve("syntax_out").resolve("output").resolve("index.html")));
        assertTrue(r.log().contains("CODE GENERATION ABORTED: lexical/syntax errors"));
    }

    @Test
    void warningsDoNotBlockGeneration() throws Exception {
        Path p = temp.resolve("warn");
        Files.createDirectories(p.resolve("templates"));
        Files.writeString(p.resolve("app.py"), "from flask import Flask, render_template\napp = Flask(__name__)\n@app.route('/')\ndef index():\n    return render_template('index.jinja', unused=1)\n");
        Files.writeString(p.resolve("templates").resolve("index.jinja"), "<img src=\"a.png\">");
        CompilationResult r = TestPaths.compile(p, temp.resolve("warn_out"), false);
        assertTrue(r.success(), r.errors().all().toString());
        assertTrue(r.errors().warningCount() >= 2, "XL002 + HT003 warnings");
        assertTrue(Files.exists(temp.resolve("warn_out").resolve("output").resolve("index.html")));
    }
}
