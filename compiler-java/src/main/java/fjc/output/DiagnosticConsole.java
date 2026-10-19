package fjc.output;

import fjc.common.CompilerError;
import fjc.common.ErrorCategory;
import fjc.pipeline.CompilationResult;
import fjc.pipeline.ProjectLayout;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Presentation-only console renderer for semantic diagnostics.
 * Semantic checks, diagnostics, source coordinates and saved reports are unchanged.
 *
 * ASCII is the safe default for Windows CMD, PowerShell and VS Code terminals.
 * --unicode remains opt-in when the terminal uses UTF-8. ANSI colors are optional.
 */
public final class DiagnosticConsole {
    // Keep descriptions inside a typical 80-column VS Code terminal even when
    // each printed line has a left margin. Paths are kept complete and unbroken.
    private static final int DEFAULT_MESSAGE_WIDTH = 52;
    private static final String RULE = "  --------------------------------------------------------------------";
    private static final String RESET = "\u001b[0m";
    private static final String RED = "\u001b[91m";
    private static final String YELLOW = "\u001b[93m";
    private static final String GREEN = "\u001b[92m";
    private static final String CYAN = "\u001b[96m";
    private static final String BOLD = "\u001b[1m";
    private static final String DIM = "\u001b[2m";

    private DiagnosticConsole() {}

    private static String styled(String content, String style, boolean color) {
        return color ? style + content + RESET : content;
    }

    public static void print(CompilationResult result, PrintStream out, boolean color) {
        print(result, out, color, false);
    }

    public static void print(CompilationResult result, PrintStream out, boolean color, boolean unicode) {
        long errors = result.errors().errorCount();
        long warnings = result.errors().warningCount();
        String errorIcon = unicode ? "✖" : "[X]";
        String warningIcon = unicode ? "⚠" : "[!]";
        String successIcon = unicode ? "✔" : "[OK]";
        String groupIcon = unicode ? "▸" : ">>";
        String locationIcon = "@";

        out.println();
        out.println(styled(RULE, CYAN, color));
        out.println("  " + styled("SEMANTIC DIAGNOSTICS", CYAN + BOLD, color));
        out.print("  " + styled((errors > 0 ? errorIcon : successIcon) + "  " + errors + " error(s)", errors > 0 ? RED + BOLD : GREEN, color));
        if (warnings > 0) {
            out.print("       " + styled(warningIcon + "  " + warnings + " warning(s)", YELLOW + BOLD, color));
        }
        out.println();
        out.println(styled(RULE, CYAN, color));

        if (errors == 0 && warnings == 0) {
            out.println("  " + styled(successIcon + "  Semantic analysis passed. No issues found.", GREEN, color));
            return;
        }

        for (ErrorCategory group : ErrorCategory.values()) {
            List<CompilerError> diagnostics = result.errors().byCategory(group);
            if (diagnostics.isEmpty()) continue;
            long groupErrors = diagnostics.stream().filter(CompilerError::isError).count();
            long groupWarnings = diagnostics.size() - groupErrors;
            out.println();
            out.println("  " + styled(groupIcon + "  " + group.title(), CYAN + BOLD, color));
            String count = groupErrors + " error(s)" + (groupWarnings > 0 ? "  |  " + groupWarnings + " warning(s)" : "");
            out.println("      " + styled(count, DIM, color));

            int index = 0;
            for (CompilerError diagnostic : diagnostics) {
                index++;
                boolean isError = diagnostic.isError();
                String icon = isError ? errorIcon : warningIcon;
                String severityStyle = isError ? RED + BOLD : YELLOW + BOLD;
                String tag = icon + "  " + diagnostic.code() + "  " + diagnostic.name();
                String path = fullSourcePath(result, diagnostic);

                out.println();
                out.println("      " + styled("#" + String.format("%02d", index) + "  " + tag, severityStyle, color));
                // The absolute path is intentionally on its own line. Do not
                // shorten it or split it into pieces that cannot be copied.
                out.println("          " + styled("FILE", CYAN, color));
                out.println("          " + path);
                out.println("          " + styled(locationIcon + "  Line " + diagnostic.line()
                        + "  |  Column " + diagnostic.column(), CYAN, color));
                out.println("          " + styled("EXPLANATION", CYAN, color));
                for (String line : wrapWords(diagnostic.message(), messageWidth())) {
                    out.println("          " + line);
                }
                out.println(styled(RULE, DIM, color));
            }
        }
    }

    /**
     * COLUMNS is honored when supplied by a terminal. Otherwise keep explanation
     * lines short enough for the common VS Code terminal width (80 columns).
     * Unlike display wrapping, this never breaks a word or quoted identifier.
     */
    private static int messageWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int value = Integer.parseInt(columns.trim());
                if (value >= 45) return Math.max(30, Math.min(DEFAULT_MESSAGE_WIDTH, value - 13));
            } catch (NumberFormatException ignored) {
                // Unknown terminal width: fall back to a conservative default.
            }
        }
        return DEFAULT_MESSAGE_WIDTH;
    }

    /** Resolves diagnostic labels back to actual files in the selected project. */
    static String fullSourcePath(CompilationResult result, CompilerError error) {
        String source = error.fileName();
        if (source == null || source.isBlank() || source.startsWith("<")) return String.valueOf(source);
        Path relative;
        try {
            relative = Path.of(source);
            if (relative.isAbsolute()) return relative.normalize().toString();
        } catch (RuntimeException ex) {
            return source;
        }
        ProjectLayout layout = result.layout();
        if (layout == null) return relative.toAbsolutePath().normalize().toString();
        Path root = layout.root();
        Path app = layout.appFile();
        if (relative.getFileName() != null && relative.getFileName().equals(app.getFileName())
                && !source.contains("/") && !source.contains("\\")) {
            return app.toAbsolutePath().normalize().toString();
        }
        Path direct = root.resolve(relative);
        if (Files.isRegularFile(direct)) return direct.toAbsolutePath().normalize().toString();
        if (source.endsWith(".jinja")) return layout.templatesDir().resolve(relative).toAbsolutePath().normalize().toString();
        if (source.endsWith(".css") || source.endsWith(".js"))
            return layout.staticDir().resolve(relative).toAbsolutePath().normalize().toString();
        return direct.toAbsolutePath().normalize().toString();
    }

    /** Wraps at whitespace only: never slices a word, variable or filename. */
    static List<String> wrapWords(String message, int width) {
        List<String> lines = new ArrayList<>();
        if (message == null || message.isBlank()) {
            lines.add("");
            return lines;
        }
        for (String paragraph : message.split("\\R", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) current.append(' ');
                current.append(word);
            }
            lines.add(current.toString());
        }
        return lines;
    }
}
