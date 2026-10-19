package fjc.common;

import java.util.Objects;

/**
 * Immutable source location: file name, 1-based line and 1-based column.
 * Every token, parse-tree node and AST node carries one of these so that
 * diagnostics can always be reported with file/line/column.
 */
public final class SourcePosition {
    private final String fileName;
    private final int line;
    private final int column;

    public SourcePosition(String fileName, int line, int column) {
        this.fileName = fileName == null ? "<memory>" : fileName;
        this.line = line;
        this.column = column;
    }

    public static SourcePosition unknown(String fileName) {
        return new SourcePosition(fileName, 0, 0);
    }

    public String fileName() { return fileName; }
    public int line() { return line; }
    public int column() { return column; }

    @Override
    public String toString() {
        return fileName + ":" + line + ":" + column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePosition)) return false;
        SourcePosition that = (SourcePosition) o;
        return line == that.line && column == that.column && fileName.equals(that.fileName);
    }

    @Override
    public int hashCode() { return Objects.hash(fileName, line, column); }
}
