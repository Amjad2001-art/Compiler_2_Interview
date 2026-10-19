package fjc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit codes and command line behaviour of the compiler entry point. */
class CompilerMainTest {

    @TempDir Path temp;

    private String run(int expectedExit, String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        int code = CompilerMain.run(args, out);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertEquals(expectedExit, code, text);
        return text;
    }

    @Test
    void validProjectExitsWithZeroAndCreatesOutputDirectories() {
        String text = run(0, TestPaths.sampleProject().toString(),
                "--output", temp.resolve("output").toString(), "--compiler-output", temp.resolve("compiler_output").toString());
        assertTrue(text.contains("RESULT: SUCCESS"));
        assertTrue(text.contains("GENERATED FILES"));
        assertTrue(Files.exists(temp.resolve("output").resolve("index.html")));
        assertTrue(Files.exists(temp.resolve("compiler_output").resolve("ast_python.json")));
    }

    @Test
    void invalidProjectExitsWithOneAndPrintsDiagnostics() {
        String text = run(1, TestPaths.invalidProject().toString(),
                "--output", temp.resolve("o").toString(), "--compiler-output", temp.resolve("c").toString());
        assertTrue(text.contains("RESULT: FAILURE"));
        assertTrue(text.contains("CODE GENERATION ABORTED"));
        assertTrue(text.contains("[PY001]"));
        assertTrue(Files.exists(temp.resolve("c").resolve("semantic_report.txt")));
    }

    @Test
    void diagnosticsDefaultToEncodingSafeAsciiAndShowAbsolutePaths() {
        String text = run(1, TestPaths.invalidProject().toString(), "--check-only", "--color", "--no-color",
                "--output", temp.resolve("ascii_out").toString(),
                "--compiler-output", temp.resolve("ascii_reports").toString());
        assertTrue(text.contains("[X] 10 ERROR(S)"), text);
        assertTrue(text.contains("[!] 1 WARNING(S)"), text);
        assertTrue(text.contains("FILE    : " + TestPaths.invalidProject().resolve("app.py").toAbsolutePath().normalize()), text);
        assertTrue(text.contains("POSITION: Line 43, Column 18"), text);
        assertTrue(text.contains("[PY005]"));
        assertTrue(text.contains("[JJ006]"));
        assertTrue(text.contains("RESULT: FAILURE"));
        for (int i = 0; i < text.length(); i++) {
            assertTrue(text.charAt(i) <= 0x7f, "Non-ASCII character at index " + i + ": " + text.charAt(i));
        }
    }

    @Test
    void unicodeDiagnosticsRequireExplicitOptIn() {
        String text = run(1, TestPaths.invalidProject().toString(), "--check-only", "--unicode", "--no-color",
                "--output", temp.resolve("unicode_out").toString(),
                "--compiler-output", temp.resolve("unicode_reports").toString());
        assertTrue(text.contains("✖ 10 ERROR(S)"));
        assertTrue(text.contains("[JJ006]"));
    }

    @Test
    void printTreesShowsBothAstsAndTheSymbolTable() {
        String text = run(0, TestPaths.sampleProject().toString(), "--check-only", "--print-trees",
                "--output", temp.resolve("o2").toString(), "--compiler-output", temp.resolve("c2").toString());
        assertTrue(text.contains("PYTHON AST"));
        assertTrue(text.contains("JINJA AST"));
        assertTrue(text.contains("|-- Assignment: products") || text.contains("`-- Assignment: products"));
        assertTrue(text.contains("JinjaTemplate: index.jinja"));
        assertTrue(text.contains("SYMBOL TABLE"));
        assertTrue(text.contains("check-only") || text.contains("analysis only"));
    }

    @Test
    void checkPythonAnalysesASingleFixture() {
        String text = run(1, "--check-python", TestPaths.semanticTests().resolve("python").resolve("02_duplicate_route.py").toString(),
                "--compiler-output", temp.resolve("c3").toString());
        assertTrue(text.contains("[PY002]"));
    }

    @Test
    void semanticTestsModeRunsEveryFixture() {
        String text = run(0, "--semantic-tests", TestPaths.semanticTests().toString(), "--compiler-output", temp.resolve("c4").toString());
        assertTrue(text.contains("python/01_undefined_variable.py"));
        assertTrue(text.contains("jinja/05_variable_out_of_scope.jinja"));
        assertTrue(text.contains("All fixtures produced semantic errors."));
    }

    @Test
    void usageErrors() {
        assertTrue(run(2).contains("Usage"));
        assertTrue(run(2, "--bogus").contains("Unknown option"));
        assertTrue(run(1, temp.resolve("does-not-exist").toString(), "--output", temp.resolve("o5").toString(),
                "--compiler-output", temp.resolve("c5").toString()).contains("CFG001"));
    }
}
