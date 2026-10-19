package fjc.semantic;

import fjc.TestPaths;
import fjc.common.CompilerError;
import fjc.common.ErrorCategory;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every Python fixture in semantic_tests/python must produce exactly its documented error code. */
class PythonSemanticFixturesTest {

    @TempDir Path temp;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "01_undefined_variable.py, PY001, 17",
            "02_duplicate_route.py, PY002, 20",
            "03_template_not_found.py, PY003, 17",
            "04_missing_context_variable.py, PY004, 18",
            "05_invalid_data_shape.py, PY005, 12",
            "06_duplicate_definition.py, PY006, 22",
            "07_duplicate_dictionary_key.py, PY007, 10",
            "08_invalid_render_template_call.py, PY008, 20",
            "09_invalid_http_method.py, PY009, 15",
            "10_route_parameter_mismatch.py, PY010, 28",
            "11_unknown_attribute.py, PY012, 17",
            "12_argument_count_mismatch.py, PY013, 29",
            "13_missing_flask_app.py, PY014, 15",
            "14_route_without_return.py, PY015, 16"
    })
    void fixtureProducesItsErrorCode(String file, String code, int line) {
        CompilationResult r = TestPaths.checkPython(TestPaths.semanticTests().resolve("python").resolve(file), temp);
        assertFalse(r.success(), file + " must fail");
        assertTrue(r.errors().hasCode(code), file + " must report " + code + " but reported " + r.errors().all());
        CompilerError first = r.errors().withCode(code).get(0);
        assertEquals(line, first.line(), "line of " + code + " in " + file + ": " + first);
        assertEquals(file, first.fileName());
        assertTrue(first.column() >= 1);
        assertEquals(ErrorCategory.PYTHON, first.category());
        // the fixture demonstrates one case: every Python-side error carries the expected code
        for (CompilerError e : r.errors().byCategory(ErrorCategory.PYTHON)) {
            if (e.isError()) assertEquals(code, e.code(), "unexpected extra Python error in " + file + ": " + e);
        }
        List<CompilerError> jinja = r.errors().byCategory(ErrorCategory.JINJA);
        assertTrue(jinja.stream().noneMatch(CompilerError::isError), "no Jinja errors expected for " + file + ": " + jinja);
        assertTrue(r.compilerOutputFiles().containsKey("semantic_report.txt"));
    }
}
