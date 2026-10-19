package fjc;

import fjc.codegen.GeneratedPage;
import fjc.common.CompilerError;
// import fjc.common.ErrorCategory;
import fjc.output.AstTreePrinter;
import fjc.output.DiagnosticConsole;
import fjc.pipeline.CompilationPipeline;
import fjc.pipeline.CompilationRequest;
import fjc.pipeline.CompilationResult;
import fjc.symbols.SymbolTablePrinter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command line entry point of the Java Flask/Jinja compiler.
 *
 * <pre>
 * java -jar compiler.jar &lt;project_dir&gt; [--output DIR] [--compiler-output DIR] [--check-only] [--print-trees]
 * java -jar compiler.jar --check-python FILE.py [--compiler-output DIR]
 * java -jar compiler.jar --semantic-tests DIR [--compiler-output DIR]
 * </pre>
 * Exit code 0 = success, 1 = compilation failed, 2 = usage error.
 */
public final class CompilerMain {

    private CompilerMain() {}

    public static void main(String[] args) {
        // The default console encoding must be the native JVM console encoding.
        // Forcing UTF-8 bytes on a CP1256/CP850 Windows terminal causes mojibake
        // such as "ظ¤". ASCII diagnostics are the default on every platform.
        boolean unicode = java.util.Arrays.asList(args).contains("--unicode")
                && !java.util.Arrays.asList(args).contains("--ascii");
        PrintStream out = unicode
                ? new PrintStream(System.out, true, StandardCharsets.UTF_8)
                : System.out;
        System.exit(run(args, out));
    }

    public static int run(String[] args, PrintStream out) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printUsage(out);
            return args.length == 0 ? 2 : 0;
        }
        Path project = null;
        Path appFile = null;
        Path output = Paths.get("output");
        Path compilerOutput = Paths.get("compiler_output");
        Path semanticTests = null;
        boolean checkOnly = false;
        boolean printTrees = false;
        boolean quiet = false;
        boolean unicode = false;
        Boolean colorOverride = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--output": output = Paths.get(need(args, ++i, a)); break;
                case "--compiler-output": compilerOutput = Paths.get(need(args, ++i, a)); break;
                case "--check-only": checkOnly = true; break;
                case "--print-trees": printTrees = true; break;
                case "--quiet": quiet = true; break;
                case "--color": colorOverride = true; break;
                case "--no-color": colorOverride = false; break;
                case "--unicode": unicode = true; break;
                case "--ascii": unicode = false; break;
                case "--check-python": appFile = Paths.get(need(args, ++i, a)); checkOnly = true; break;
                case "--semantic-tests": semanticTests = Paths.get(need(args, ++i, a)); break;
                default:
                    if (a.startsWith("--")) { out.println("Unknown option: " + a); printUsage(out); return 2; }
                    project = Paths.get(a);
            }
        }
        if (semanticTests != null) return runSemanticTests(semanticTests, compilerOutput, out);
        if (appFile != null && project == null) project = appFile.toAbsolutePath().getParent();
        if (project == null) { printUsage(out); return 2; }

        CompilationRequest request = new CompilationRequest(project, appFile, output, compilerOutput, checkOnly);
        CompilationResult result = new CompilationPipeline().compile(request);
        if (!quiet) {
            boolean color = colorOverride != null ? colorOverride
                    : System.console() != null && System.getenv("NO_COLOR") == null;
            printSummary(result, out, printTrees, color, unicode);
        }
        return result.success() ? 0 : 1;
    }

    private static String need(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException("Option " + option + " needs a value");
        return args[i];
    }

    private static void printUsage(PrintStream out) {
        out.println("Flask/Jinja compiler (Java)");
        out.println("Usage:");
        out.println("  java -jar compiler.jar <project_dir> [--output DIR] [--compiler-output DIR] [--check-only] [--print-trees] [--quiet] [--color|--no-color] [--ascii|--unicode]");
        out.println("  java -jar compiler.jar --check-python FILE.py [--compiler-output DIR]");
        out.println("  java -jar compiler.jar --semantic-tests DIR [--compiler-output DIR]");
        out.println();
        out.println("  <project_dir> must contain app.py, templates/*.jinja and static/ (style.css, script.js).");
        out.println("  Generated pages go to --output (default ./output); reports, ASTs and logs go to");
        out.println("  --compiler-output (default ./compiler_output; only the four required files).");
        out.println("  Extra trees, symbols and context reports go to sibling ./compiler_details.");
    }

    static void printSummary(CompilationResult r, PrintStream out, boolean printTrees, boolean color) {
        printSummary(r, out, printTrees, color, false);
    }

    static void printSummary(CompilationResult r, PrintStream out, boolean printTrees, boolean color, boolean unicode) {
        out.println("==================================================================");
        out.println(" Flask/Jinja Compiler (Java) - " + (r.layout() == null ? "?" : r.layout().name()));
        out.println("==================================================================");
        // خيار طباعة الأشجار يتيح عرض عقد الأشجار وجدول الرموز من الطرفية للمناقشة.
        if (printTrees) {
            AstTreePrinter printer = new AstTreePrinter(true);
            if (r.pythonAst() != null) {
                out.println();
                out.println("PYTHON AST");
                out.print(printer.print(r.pythonAst()));
            }
            if (r.jinjaAst() != null) {
                out.println();
                out.println("JINJA AST");
                out.print(printer.print(r.jinjaAst()));
            }
            for (fjc.css.Stylesheet s : r.stylesheets()) {
                out.println();
                out.println("CSS AST");
                out.print(printer.print(s));
            }
            if (r.symbols() != null) {
                out.println();
                out.print(new SymbolTablePrinter(true).print(r.symbols()));
            }
        }
        out.println();
        out.println("Stage reached: " + r.stageReached());
        if (r.pythonParseTree() != null && r.pythonAst() != null) {
            out.println("Python: parse tree " + r.pythonParseTree().nodeCount() + " nodes -> AST " + r.pythonAst().nodeCount() + " nodes");
        }
        if (r.jinjaAst() != null) {
            int parse = 0;
            for (fjc.parsetree.RuleNode n : r.jinjaParseTrees().values()) parse += n.nodeCount();
            out.println("Jinja:  parse trees " + parse + " nodes -> AST " + r.jinjaAst().nodeCount() + " nodes (" + r.jinjaAst().templates().size() + " templates)");
        }
        DiagnosticConsole.print(r, out, color, unicode);
        if (r.success()) {
            out.println();
            if (r.generatedPages().isEmpty()) {
                out.println("Semantic analysis passed (no pages generated in check-only mode).");
            } else {
                out.println("GENERATED FILES (" + r.outputDir() + "):");
                for (GeneratedPage p : r.generatedPages()) out.println("  " + p);
                out.println("COPIED SUPPORT FILES:");
                for (Path p : r.copiedFiles()) out.println("  " + p.getFileName());
            }
        } else {
            out.println();
            out.println("CODE GENERATION ABORTED: " + r.failureReason());
        }
        out.println("COMPILER OUTPUT (" + r.compilerOutputDir() + "):");
        for (String f : List.of("ast_python.json", "ast_jinja.json", "semantic_report.txt", "generation_log.txt"))
            if (r.compilerOutputFiles().containsKey(f)) out.println("  " + f);
        out.println("AUXILIARY REPORTS (" + r.compilerOutputDir().resolveSibling("compiler_details") + "):");
        for (Map.Entry<String, Path> file : r.compilerOutputFiles().entrySet())
            if (!List.of("ast_python.json", "ast_jinja.json", "semantic_report.txt", "generation_log.txt").contains(file.getKey()))
                out.println("  " + file.getKey());
        out.println("RESULT: " + (r.success() ? "SUCCESS" : "FAILURE"));
    }

    // ---------------------------------------------------------- fixtures

    /** Runs every semantic fixture and prints which error codes each one produced. */
    static int runSemanticTests(Path dir, Path compilerOutput, PrintStream out) {
        Map<String, List<String>> results = new LinkedHashMap<>();
        boolean allDetected = true;
        // Semantic fixture outputs are auxiliary artifacts, not the four course reports.
        Path fixtureOutput = compilerOutput.toAbsolutePath().normalize()
                .resolveSibling("compiler_details").resolve("semantic_tests");
        Path pythonDir = dir.resolve("python");
        if (Files.isDirectory(pythonDir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(pythonDir)) {
                files = s.filter(p -> p.toString().endsWith(".py")).sorted().collect(Collectors.toList());
            } catch (IOException e) {
                out.println("Cannot list " + pythonDir + ": " + e.getMessage());
                return 2;
            }
            for (Path file : files) {
                String stem = file.getFileName().toString().replace(".py", "");
                CompilationRequest req = new CompilationRequest(pythonDir, file, fixtureOutput.resolve("python").resolve(stem).resolve("output"),
                        fixtureOutput.resolve("python").resolve(stem), true);
                CompilationResult r = new CompilationPipeline().compile(req);
                List<String> codes = codes(r, null);
                results.put("python/" + file.getFileName(), codes);
                if (codes.isEmpty()) allDetected = false;
            }
        }
        Path jinjaDir = dir.resolve("jinja");
        if (Files.isDirectory(jinjaDir)) {
            CompilationRequest req = new CompilationRequest(jinjaDir, null, fixtureOutput.resolve("jinja").resolve("output"),
                    fixtureOutput.resolve("jinja"), true);
            CompilationResult r = new CompilationPipeline().compile(req);
            List<String> templates = new ArrayList<>();
            if (r.layout() != null) templates = r.layout().templateNames();
            for (String t : templates) {
                List<String> codes = codes(r, t);
                results.put("jinja/" + t, codes);
                if (codes.isEmpty()) allDetected = false;
            }
        }
        out.println("SEMANTIC FIXTURES (" + dir + ")");
        out.println(String.format("%-45s %s", "fixture", "error codes detected"));
        for (Map.Entry<String, List<String>> e : results.entrySet()) {
            out.println(String.format("%-45s %s", e.getKey(), e.getValue().isEmpty() ? "NONE (unexpected)" : String.join(", ", e.getValue())));
        }
        out.println(allDetected ? "All fixtures produced semantic errors." : "Some fixtures produced no error!");
        return allDetected ? 0 : 1;
    }

    private static List<String> codes(CompilationResult r, String fileFilter) {
        TreeSet<String> codes = new TreeSet<>();
        for (CompilerError e : r.errors().all()) {
            if (!e.isError()) continue;
            if (fileFilter != null && !e.fileName().equals(fileFilter)) continue;
            codes.add(e.code() + " " + e.name());
        }
        return new ArrayList<>(codes);
    }
}
