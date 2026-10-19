package fjc.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Timestamped log of a compilation run (written to compiler_output/generation_log.txt). */
public final class GenerationLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final List<String> lines = new ArrayList<>();

    public void info(String message) {
        lines.add("[" + LocalTime.now().format(TIME) + "] " + message);
    }

    public void section(String title) {
        lines.add("");
        lines.add("---- " + title + " ----");
    }

    public List<String> lines() { return Collections.unmodifiableList(lines); }

    public String text() { return String.join(System.lineSeparator(), lines) + System.lineSeparator(); }

    public boolean contains(String fragment) {
        for (String l : lines) if (l.contains(fragment)) return true;
        return false;
    }

    public void writeTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text(), StandardCharsets.UTF_8);
    }

    /** A path relative to the working directory when possible (keeps logs machine independent). */
    public static String display(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (abs.startsWith(cwd)) {
            String rel = cwd.relativize(abs).toString().replace('\\', '/');
            return rel.isEmpty() ? "." : rel;
        }
        return abs.toString();
    }
}
