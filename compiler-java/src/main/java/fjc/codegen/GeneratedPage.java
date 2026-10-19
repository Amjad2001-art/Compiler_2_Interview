package fjc.codegen;

import java.nio.file.Path;

/** One HTML file produced by the generator. */
public final class GeneratedPage {
    private final String templateName;
    private final String fileName;
    private final Path path;
    private final int instances;
    private final long bytes;

    public GeneratedPage(String templateName, String fileName, Path path, int instances, long bytes) {
        this.templateName = templateName;
        this.fileName = fileName;
        this.path = path;
        this.instances = instances;
        this.bytes = bytes;
    }

    public String templateName() { return templateName; }
    public String fileName() { return fileName; }
    public Path path() { return path; }
    public int instances() { return instances; }
    public long bytes() { return bytes; }

    @Override public String toString() {
        return fileName + " (from " + templateName + ", " + instances + " instance(s), " + bytes + " bytes)";
    }
}
