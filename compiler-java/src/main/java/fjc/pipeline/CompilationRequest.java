package fjc.pipeline;

import java.nio.file.Path;

/** What to compile and where to write the results. All paths may be relative. */
public final class CompilationRequest {
    private final Path projectDir;
    private final Path appFile;
    private final Path outputDir;
    private final Path compilerOutputDir;
    private final boolean checkOnly;

    public CompilationRequest(Path projectDir, Path appFile, Path outputDir, Path compilerOutputDir, boolean checkOnly) {
        this.projectDir = projectDir;
        this.appFile = appFile;
        this.outputDir = outputDir;
        this.compilerOutputDir = compilerOutputDir;
        this.checkOnly = checkOnly;
    }

    public static CompilationRequest forProject(Path projectDir, Path outputDir, Path compilerOutputDir) {
        return new CompilationRequest(projectDir, null, outputDir, compilerOutputDir, false);
    }

    public Path projectDir() { return projectDir; }
    /** Explicit Python file to analyse (null = projectDir/app.py). */
    public Path appFile() { return appFile; }
    public Path outputDir() { return outputDir; }
    public Path compilerOutputDir() { return compilerOutputDir; }
    /** Run analysis only: never generate pages. */
    public boolean checkOnly() { return checkOnly; }
}
