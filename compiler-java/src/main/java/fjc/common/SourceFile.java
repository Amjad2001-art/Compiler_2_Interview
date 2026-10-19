package fjc.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A source file loaded into memory (display name + normalised text). */
public final class SourceFile {
    private final String name;
    private final String text;
    private final Path path;

    public SourceFile(String name, String text) {
        this(name, text, null);
    }

    public SourceFile(String name, String text, Path path) {
        this.name = name;
        this.text = text.replace("\r\n", "\n").replace('\r', '\n');
        this.path = path;
    }

    public static SourceFile read(Path path, String displayName) throws IOException {
        return new SourceFile(displayName, Files.readString(path, StandardCharsets.UTF_8), path);
    }

    public String name() { return name; }
    public String text() { return text; }
    public Path path() { return path; }
}
