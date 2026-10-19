package fjc.symbols;

/** Kinds of symbols recorded during semantic analysis. */
public enum SymbolKind {
    BUILTIN,            // len, int, str, render_template, ...
    IMPORT,             // names bound by import statements
    MODULE_VARIABLE,    // module-level assignment (products = [...])
    LOCAL_VARIABLE,     // assignment inside a function
    PARAMETER,          // function parameter
    LOOP_VARIABLE,      // for-loop target (Python or Jinja)
    FUNCTION,           // def name(...)
    ROUTE,              // Flask route endpoint
    TEMPLATE,           // a Jinja template file
    CONTEXT_VARIABLE,   // variable passed to a template by render_template
    TEMPLATE_GLOBAL,    // url_for, loop, range (Jinja built-ins)
    CSS_CUSTOM_PROPERTY // --name in a stylesheet
}
