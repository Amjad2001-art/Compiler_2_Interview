package fjc.pipeline;

import fjc.codegen.GeneratedPage;
import fjc.common.ErrorCollector;
import fjc.css.Stylesheet;
import fjc.evaluate.ContextData;
import fjc.jinja.ast.JinjaProject;
import fjc.output.GenerationLog;
import fjc.parsetree.RuleNode;
import fjc.python.ast.PythonProgram;
import fjc.semantic.FlaskModel;
import fjc.symbols.SymbolTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything a compilation run produced (for the CLI, the tests and the reports). */
public final class CompilationResult {
    public enum Stage { SETUP, LEXICAL_SYNTAX, SEMANTIC, CONTEXT, GENERATION, DONE }

    boolean success;
    Stage stageReached = Stage.SETUP;
    String failureReason;
    final ErrorCollector errors = new ErrorCollector();
    final GenerationLog log = new GenerationLog();
    ProjectLayout layout;
    RuleNode pythonParseTree;
    PythonProgram pythonAst;
    final Map<String, RuleNode> jinjaParseTrees = new LinkedHashMap<>();
    JinjaProject jinjaAst;
    final List<Stylesheet> stylesheets = new ArrayList<>();
    final Map<String, RuleNode> cssParseTrees = new LinkedHashMap<>();
    SymbolTable symbols;
    FlaskModel model;
    ContextData contexts;
    final List<GeneratedPage> generatedPages = new ArrayList<>();
    final List<Path> copiedFiles = new ArrayList<>();
    final Map<String, Path> compilerOutputFiles = new LinkedHashMap<>();
    Path outputDir;
    Path compilerOutputDir;

    public boolean success() { return success; }
    public Stage stageReached() { return stageReached; }
    public String failureReason() { return failureReason; }
    public ErrorCollector errors() { return errors; }
    public GenerationLog log() { return log; }
    public ProjectLayout layout() { return layout; }
    public RuleNode pythonParseTree() { return pythonParseTree; }
    public PythonProgram pythonAst() { return pythonAst; }
    public Map<String, RuleNode> jinjaParseTrees() { return jinjaParseTrees; }
    public JinjaProject jinjaAst() { return jinjaAst; }
    public List<Stylesheet> stylesheets() { return stylesheets; }
    public Map<String, RuleNode> cssParseTrees() { return cssParseTrees; }
    public SymbolTable symbols() { return symbols; }
    public FlaskModel model() { return model; }
    public ContextData contexts() { return contexts; }
    public List<GeneratedPage> generatedPages() { return generatedPages; }
    public List<Path> copiedFiles() { return copiedFiles; }
    public Map<String, Path> compilerOutputFiles() { return compilerOutputFiles; }
    public Path outputDir() { return outputDir; }
    public Path compilerOutputDir() { return compilerOutputDir; }
}
