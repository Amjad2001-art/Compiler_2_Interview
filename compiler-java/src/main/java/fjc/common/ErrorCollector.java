package fjc.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collects diagnostics from every phase; de-duplicates identical entries. */
public final class ErrorCollector {
    private final List<CompilerError> errors = new ArrayList<>();
    private final Set<String> seen = new LinkedHashSet<>();

    public void add(CompilerError error) {
        if (seen.add(error.format())) {
            errors.add(error);
        }
    }

    public void addAll(Iterable<CompilerError> more) {
        for (CompilerError e : more) add(e);
    }

    public void error(ErrorCategory category, String code, String name, String message, SourcePosition pos) {
        add(CompilerError.error(category, code, name, message, pos));
    }

    public void warning(ErrorCategory category, String code, String name, String message, SourcePosition pos) {
        add(CompilerError.warning(category, code, name, message, pos));
    }

    public boolean hasErrors() {
        return errors.stream().anyMatch(CompilerError::isError);
    }

    public long errorCount() { return errors.stream().filter(CompilerError::isError).count(); }
    public long warningCount() { return errors.stream().filter(e -> !e.isError()).count(); }

    public List<CompilerError> all() { return Collections.unmodifiableList(errors); }

    public List<CompilerError> byCategory(ErrorCategory category) {
        List<CompilerError> out = new ArrayList<>();
        for (CompilerError e : errors) if (e.category() == category) out.add(e);
        return out;
    }

    public List<CompilerError> withCode(String code) {
        List<CompilerError> out = new ArrayList<>();
        for (CompilerError e : errors) if (e.code().equals(code)) out.add(e);
        return out;
    }

    public boolean hasCode(String code) { return !withCode(code).isEmpty(); }

    public List<CompilerError> sorted() {
        List<CompilerError> copy = new ArrayList<>(errors);
        copy.sort(Comparator.comparing((CompilerError e) -> e.category().ordinal())
                .thenComparing(CompilerError::fileName)
                .thenComparingInt(CompilerError::line)
                .thenComparingInt(CompilerError::column));
        return copy;
    }

    public int size() { return errors.size(); }
}
