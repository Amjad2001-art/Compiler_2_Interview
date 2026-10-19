package fjc;

import fjc.pipeline.CompilationPipeline;
import fjc.pipeline.CompilationRequest;
import fjc.pipeline.CompilationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Locates the repository folders (sample_project, semantic_tests, ...) from the test working directory. */
public final class TestPaths {
    private TestPaths() {}

    /** The repository root: the directory that contains sample_project/. */
    public static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("sample_project")) && Files.isDirectory(dir.resolve("compiler-java"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate the repository root (sample_project/) from " + Paths.get("").toAbsolutePath());
    }

    public static Path sampleProject() { return repoRoot().resolve("sample_project"); }
    public static Path invalidProject() { return repoRoot().resolve("sample_project_invalid"); }
    public static Path semanticTests() { return repoRoot().resolve("semantic_tests"); }

    /** Compiles a project into temporary output directories. */
    public static CompilationResult compile(Path project, Path tempDir, boolean checkOnly) {
        CompilationRequest req = new CompilationRequest(project, null, tempDir.resolve("output"), tempDir.resolve("compiler_output"), checkOnly);
        return new CompilationPipeline().compile(req);
    }

    /** Analyses a single Python fixture (templates are taken from the fixture's sibling templates/ directory). */
    public static CompilationResult checkPython(Path pythonFile, Path tempDir) {
        CompilationRequest req = new CompilationRequest(pythonFile.getParent(), pythonFile, tempDir.resolve("output"), tempDir.resolve("compiler_output"), true);
        return new CompilationPipeline().compile(req);
    }
}
