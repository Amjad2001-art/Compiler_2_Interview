package fjc.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovers the input files of a Flask/Jinja project:
 * <ul>
 *   <li>{@code app.py} (or an explicit file) – ANALYSED INPUT</li>
 *   <li>{@code templates/*.jinja} (or {@code *.jinja} next to the app) – ANALYSED INPUT</li>
 *   <li>{@code static/**} (or css/js files next to the app) – COPIED SUPPORT FILES</li>
 * </ul>
 */
public final class ProjectLayout {
    private final Path root;
    private final Path appFile;
    private final Path templatesDir;
    private final Path staticDir;
    private final List<Path> templateFiles;
    private final List<Path> staticFiles;

    private ProjectLayout(Path root, Path appFile, Path templatesDir, Path staticDir, List<Path> templateFiles, List<Path> staticFiles) {
        this.root = root;
        this.appFile = appFile;
        this.templatesDir = templatesDir;
        this.staticDir = staticDir;
        this.templateFiles = Collections.unmodifiableList(templateFiles);
        this.staticFiles = Collections.unmodifiableList(staticFiles);
    }

    /**
     * @param projectDir the project directory
     * @param appOverride an explicit Python file to analyse instead of {@code projectDir/app.py} (may be null)
     */
    public static ProjectLayout discover(Path projectDir, Path appOverride) throws IOException {
        Path root = projectDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Project directory does not exist: " + root);
        Path app = appOverride != null ? appOverride.toAbsolutePath().normalize() : root.resolve("app.py");
        if (!Files.isRegularFile(app)) throw new IOException("Flask application file not found: " + app);

        Path templatesDir = Files.isDirectory(root.resolve("templates")) ? root.resolve("templates") : root;
        List<Path> templates;
        try (Stream<Path> s = Files.list(templatesDir)) {
            templates = s.filter(p -> p.getFileName().toString().endsWith(".jinja")).sorted().collect(Collectors.toList());
        }
        Path staticDir = Files.isDirectory(root.resolve("static")) ? root.resolve("static") : root;
        List<Path> statics = new ArrayList<>();
        if (staticDir.equals(root)) {
            try (Stream<Path> s = Files.list(root)) {
                statics = s.filter(p -> Files.isRegularFile(p) && (p.toString().endsWith(".css") || p.toString().endsWith(".js")))
                        .sorted().collect(Collectors.toList());
            }
        } else {
            try (Stream<Path> s = Files.walk(staticDir)) {
                statics = s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().startsWith("."))
                        .sorted().collect(Collectors.toList());
            }
        }
        return new ProjectLayout(root, app, templatesDir, staticDir, templates, statics);
    }

    public Path root() { return root; }
    public Path appFile() { return appFile; }
    public Path templatesDir() { return templatesDir; }
    public Path staticDir() { return staticDir; }
    public List<Path> templateFiles() { return templateFiles; }
    public List<Path> staticFiles() { return staticFiles; }
    public String name() { return root.getFileName() == null ? root.toString() : root.getFileName().toString(); }

    public List<String> templateNames() {
        List<String> out = new ArrayList<>();
        for (Path p : templateFiles) out.add(p.getFileName().toString());
        return out;
    }

    /** Static file names relative to the static directory, with '/' separators (e.g. "style.css"). */
    public List<String> staticNames() {
        List<String> out = new ArrayList<>();
        for (Path p : staticFiles) out.add(staticDir.relativize(p).toString().replace('\\', '/'));
        return out;
    }

    public List<Path> cssFiles() {
        List<Path> out = new ArrayList<>();
        for (Path p : staticFiles) if (p.toString().endsWith(".css")) out.add(p);
        return out;
    }

    /** Display name of a file relative to the project root. */
    public String display(Path file) {
        try {
            return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }
}
