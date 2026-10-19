package fjc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural rules: the code generator works on the validated AST + context
 * data only and never depends on the symbol table; nothing in the compiler
 * shells out to Python.
 */
class ArchitectureTest {

    private static List<Path> sources(String pkg) throws IOException {
        Path root = Paths.get("src", "main", "java", "fjc", pkg);
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    @Test
    void codeGeneratorDoesNotDependOnTheSymbolTable() throws IOException {
        List<Path> files = sources("codegen");
        assertFalse(files.isEmpty());
        for (Path f : files) {
            String text = Files.readString(f);
            assertFalse(text.contains("fjc.symbols"), f + " must not import the symbol table package");
            assertFalse(text.contains("SymbolTable"), f + " must not reference SymbolTable");
        }
    }

    @Test
    void evaluatorDoesNotDependOnTheSymbolTableEither() throws IOException {
        for (Path f : sources("evaluate")) {
            assertFalse(Files.readString(f).contains("fjc.symbols"), f.toString());
        }
    }

    @Test
    void compilerNeverInvokesAPythonRuntime() throws IOException {
        Path root = Paths.get("src", "main", "java");
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String text = Files.readString(f);
                assertFalse(text.contains("ProcessBuilder") || text.contains("Runtime.getRuntime().exec"), f + " must not spawn processes");
                assertFalse(text.contains("python.exe") || text.contains("\"python3\"") || text.contains("import ast"), f + " must not delegate to a Python interpreter");
            }
        }
    }

    @Test
    void sourcesContainNoHardCodedMachinePaths() throws IOException {
        Path root = Paths.get("src");
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".java") && !p.endsWith("ArchitectureTest.java")).collect(Collectors.toList())) {
                String text = Files.readString(f);
                assertFalse(text.matches("(?s).*[A-Za-z]:\\\\Users.*"), f + " contains an absolute Windows path");
                assertFalse(text.contains("/home/"), f + " contains an absolute Unix home path");
            }
        }
        assertTrue(Files.exists(Paths.get("pom.xml")));
    }
}
