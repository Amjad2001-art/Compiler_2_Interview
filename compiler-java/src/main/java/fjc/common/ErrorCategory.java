package fjc.common;

/**
 * Which part of the compiler produced a diagnostic. The semantic report groups
 * diagnostics by category so Python/Flask errors and Jinja errors are clearly
 * distinguishable from each other and from cross-language errors.
 */
public enum ErrorCategory {
    LEXICAL("Lexical errors"),
    SYNTAX("Syntax errors"),
    PYTHON("Python/Flask semantic errors"),
    JINJA("Jinja semantic errors"),
    CROSS("Cross-language (Python <-> Jinja) semantic errors"),
    HTML("HTML semantic errors"),
    CSS("CSS errors"),
    GENERATION("Context preparation / code generation errors");

    private final String title;

    ErrorCategory(String title) { this.title = title; }

    public String title() { return title; }
}
