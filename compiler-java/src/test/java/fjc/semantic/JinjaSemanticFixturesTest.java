package fjc.semantic;

import fjc.TestPaths;
import fjc.common.CompilerError;
import fjc.common.ErrorCategory;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Jinja fixtures form one project (semantic_tests/jinja: app.py provides the
 * context, each *.jinja file demonstrates one semantic error).
 */
class JinjaSemanticFixturesTest {

    static CompilationResult result;

    @TempDir static Path temp;

    @BeforeAll
    static void compileFixtureProject() {
        result = TestPaths.compile(TestPaths.semanticTests().resolve("jinja"), temp, true);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "01_undefined_variable.jinja, JJ001, JINJA, 3",
            "02_invalid_property.jinja, JJ002, JINJA, 4",
            "03_non_iterable_loop.jinja, JJ003, JINJA, 4",
            "04_unknown_endpoint.jinja, JJ004, JINJA, 4",
            "05_variable_out_of_scope.jinja, JJ005, JINJA, 7",
            "06_unknown_filter.jinja, JJ006, JINJA, 2",
            "07_url_for_parameter_mismatch.jinja, JJ007, JINJA, 4",
            "08_unknown_test.jinja, JJ008, JINJA, 3",
            "09_filter_argument_mismatch.jinja, JJ010, JINJA, 2",
            "10_static_file_not_found.jinja, JJ011, JINJA, 2",
            "11_unknown_function.jinja, JJ012, JINJA, 3",
            "12_incompatible_comparison.jinja, JJ013, JINJA, 3",
            "13_form_method_mismatch.jinja, XL001, CROSS, 2",
            "14_duplicate_html_id.jinja, HT005, HTML, 9"
    })
    void fixtureProducesItsErrorCode(String file, String code, String category, int line) {
        List<CompilerError> inFile = result.errors().all().stream().filter(e -> e.fileName().equals(file)).toList();
        assertFalse(inFile.isEmpty(), file + " must produce a diagnostic");
        CompilerError first = inFile.stream().filter(e -> e.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError(file + " must report " + code + " but reported " + inFile));
        assertEquals(ErrorCategory.valueOf(category), first.category());
        assertEquals(line, first.line(), "line of " + code + ": " + first);
        assertTrue(first.column() >= 1);
        for (CompilerError e : inFile) {
            if (e.isError()) assertEquals(code, e.code(), "unexpected extra error in " + file + ": " + e);
        }
    }

    @Test
    void fixtureProjectFailsWithoutPythonErrorsAndWritesReport() throws Exception {
        assertFalse(result.success());
        assertTrue(result.errors().byCategory(ErrorCategory.PYTHON).stream().noneMatch(CompilerError::isError),
                "the fixture app.py itself is valid: " + result.errors().byCategory(ErrorCategory.PYTHON));
        String report = Files.readString(result.compilerOutputFiles().get("semantic_report.txt"));
        assertTrue(report.contains("== Jinja semantic errors"));
        assertTrue(report.contains("[JJ001]") && report.contains("[XL001]") && report.contains("[HT005]"));
        assertTrue(report.contains("SEMANTIC ANALYSIS FAILED"));
    }
}
