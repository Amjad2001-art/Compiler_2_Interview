package fjc.output;

import fjc.TestPaths;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticConsoleTest {
    @TempDir Path temp;

    @Test
    void diagnosticsAreReadableAndShowFullPathsAndCoordinates() throws Exception {
        CompilationResult result = TestPaths.compile(TestPaths.invalidProject(), temp, true);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            DiagnosticConsole.print(result, out, false);
        }
        String displayed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(displayed.contains("[X]  10 error(s)"));
        assertFalse(displayed.contains("warning(s)"), "The invalid fixture has no warnings");
        assertTrue(displayed.contains("PY004  MissingContextVariable"));
        assertTrue(displayed.contains("JJ006  UnknownFilter"));
        assertTrue(displayed.contains(TestPaths.invalidProject().resolve("app.py").toAbsolutePath().normalize().toString()));
        assertTrue(displayed.contains(TestPaths.invalidProject().resolve("templates/add_product.jinja").toAbsolutePath().normalize().toString()));
        assertTrue(displayed.contains("Line 109  |  Column 12"));
        assertTrue(displayed.contains("EXPLANATION"));
        assertFalse(displayed.contains("\u001b["), "Plain output must not inject ANSI escape codes");
        assertFalse(displayed.contains("ظ¤"), "Default output must not contain mojibake");
    }

    @Test
    void wordWrapNeverBreaksIdentifiersOrSourceFilenames() {
        assertEquals(
                java.util.List.of("This", "unbroken_single_identifier.py:123:42", "must stay"),
                DiagnosticConsole.wrapWords("This unbroken_single_identifier.py:123:42 must stay", 10));
        assertEquals(
                java.util.List.of("Route 'product_detail' renders", "'product_detail.jinja'"),
                DiagnosticConsole.wrapWords("Route 'product_detail' renders 'product_detail.jinja'", 33));
    }
}
