package fjc.pipeline;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fjc.codegen.CodeGenerator;
import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourceFile;
import fjc.common.SourcePosition;
import fjc.common.Token;
import fjc.css.CssAnalyzer;
import fjc.css.CssAstBuilder;
import fjc.css.CssLexer;
import fjc.css.CssParser;
import fjc.css.Stylesheet;
// import fjc.evaluate.ContextData;
import fjc.evaluate.ContextPreparer;
import fjc.evaluate.PageContext;
import fjc.evaluate.Value;
import fjc.jinja.ast.JinjaAstBuilder;
import fjc.jinja.ast.JinjaProject;
import fjc.jinja.ast.JinjaTemplate;
import fjc.jinja.lexer.JinjaLexer;
import fjc.jinja.parser.JinjaParser;
import fjc.output.AstJsonSerializer;
import fjc.output.AstTreePrinter;
import fjc.output.FileCopier;
import fjc.output.GenerationLog;
import fjc.output.SemanticReportWriter;
import fjc.parsetree.ParseTreePrinter;
import fjc.parsetree.RuleNode;
import fjc.python.ast.PythonAstBuilder;
import fjc.python.lexer.PythonLexer;
import fjc.python.parser.PythonParser;
import fjc.semantic.CrossLanguageAnalyzer;
import fjc.semantic.HtmlValidator;
import fjc.semantic.JinjaSemanticAnalyzer;
import fjc.semantic.PythonSemanticAnalyzer;
import fjc.symbols.SymbolTable;
import fjc.symbols.SymbolTablePrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.StandardCopyOption;

/**
 * The compiler pipeline. Stages run strictly in this order and code generation
 * is structurally unreachable while any error exists:
 *
 * <pre>
 *   lexing → parsing (parse trees) → AST construction
 *   → semantic analysis (symbol table, Python, Jinja, HTML, CSS, cross-language)
 *   → if any error: STOP (report + aborted log, no output files touched)
 *   → context/data preparation (evaluate the Python AST)
 *   → code generation (Jinja AST + context data → HTML) → copy support files
 * </pre>
 */
public final class CompilationPipeline {

    /** Only the four files specified by the course are written to compiler_output/. */
    private static final Set<String> REQUIRED_REPORTS = Set.of(
            "ast_python.json", "ast_jinja.json", "semantic_report.txt", "generation_log.txt");

    /** Auxiliary reports are retained for the tree viewer, context inspector and tests. */
    private static Path detailsDir(Path compilerOutputDir) {
        return compilerOutputDir.resolveSibling("compiler_details");
    }

    /** A user can safely upgrade over an older project without leaving extra files in compiler_output/. */
    private void migrateLegacyReports(CompilationResult r) {
        Path details = detailsDir(r.compilerOutputDir);
        for (String filename : List.of("ast_css.json", "ast_css_tree.txt", "ast_python_tree.txt",
                "ast_jinja_tree.txt", "parse_tree_python.txt", "parse_tree_jinja.txt",
                "symbol_table.txt", "context_data.json")) {
            Path legacy = r.compilerOutputDir.resolve(filename);
            if (!Files.isRegularFile(legacy)) continue;
            try {
                Files.createDirectories(details);
                Files.move(legacy, details.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                r.errors.error(ErrorCategory.GENERATION, "GEN001", "IOError",
                        "Cannot move legacy auxiliary report " + filename + ": " + e.getMessage(),
                        SourcePosition.unknown(legacy.toString()));
            }
        }
        Path oldFixtures = r.compilerOutputDir.resolve("semantic_tests");
        if (Files.isDirectory(oldFixtures)) {
            try {
                Files.createDirectories(details);
                Path newFixtures = details.resolve("semantic_tests");
                if (Files.exists(newFixtures)) {
                    int n = 1;
                    while (Files.exists(details.resolve("semantic_tests_legacy_" + n))) n++;
                    newFixtures = details.resolve("semantic_tests_legacy_" + n);
                }
                Files.move(oldFixtures, newFixtures);
            } catch (IOException e) {
                r.errors.error(ErrorCategory.GENERATION, "GEN001", "IOError",
                        "Cannot move legacy fixture reports: " + e.getMessage(),
                        SourcePosition.unknown(oldFixtures.toString()));
            }
        }
    }

    public CompilationResult compile(CompilationRequest request) {
        CompilationResult r = new CompilationResult();
        r.outputDir = request.outputDir().toAbsolutePath().normalize();
        r.compilerOutputDir = request.compilerOutputDir().toAbsolutePath().normalize();
        migrateLegacyReports(r);
        r.log.info("Compilation started");
        r.log.info("Input project: " + GenerationLog.display(request.projectDir()));
        r.log.info("Output directory (generated pages): " + GenerationLog.display(r.outputDir));
        r.log.info("Required course reports: " + GenerationLog.display(r.compilerOutputDir));
        r.log.info("Auxiliary compiler reports: " + GenerationLog.display(detailsDir(r.compilerOutputDir)));

        // ---------------------------------------------------------- setup
        try {
            r.layout = ProjectLayout.discover(request.projectDir(), request.appFile());
        } catch (IOException e) {
            r.errors.error(ErrorCategory.GENERATION, "CFG001", "InvalidProject", e.getMessage(),
                    SourcePosition.unknown(request.projectDir().toString()));
            return abort(r, "invalid project layout");
        }
        ProjectLayout layout = r.layout;
        r.log.info("Analysed input: " + layout.display(layout.appFile()));
        for (Path t : layout.templateFiles()) r.log.info("Analysed input: " + layout.display(t));
        for (Path s : layout.staticFiles()) r.log.info("Support file (" + (s.toString().endsWith(".css") ? "analysed CSS, copied unchanged" : "copied unchanged") + "): " + layout.display(s));
        if (layout.templateFiles().isEmpty()) r.log.info("Warning: no .jinja templates found in " + layout.templatesDir());

        // ------------------------------------------- lexing, parsing, AST
        // تنفيذ مرحلتي التحليل اللفظي والنحوي ثم بناء أشجار AST من ملفات المشروع.
        r.stageReached = CompilationResult.Stage.LEXICAL_SYNTAX;
        r.log.section("Lexical and syntax analysis");
        try {
            SourceFile app = SourceFile.read(layout.appFile(), layout.display(layout.appFile()));
            // هنا يقسم المحلل اللفظي مصدر بايثون إلى رموز مع مواقعها؛ لم يبدأ بناء الشجرة بعد.
            List<Token> tokens = new PythonLexer(app).tokenize();
            r.log.info("Python lexer: " + tokens.size() + " tokens from " + app.name());
            // يستهلك المحلل النحوي رموز بايثون حسب قواعد اللغة لينشئ شجرة تحليل مفصلة.
            r.pythonParseTree = new PythonParser(tokens).parseModule();
            r.log.info("Python parser: parse tree with " + r.pythonParseTree.nodeCount() + " nodes");
            // هنا تتحول شجرة القواعد التفصيلية إلى شجرة مجردة تحتفظ بالمعاني المهمة من البرنامج.
            r.pythonAst = new PythonAstBuilder(app.name()).build(r.pythonParseTree);
            r.log.info("Python AST built: " + r.pythonAst.nodeCount() + " nodes (" + r.pythonAst.routes().size() + " routes)");
        } catch (CompilerException e) {
            r.errors.add(e.error());
            r.log.info("Python analysis failed: " + e.error().format());
        } catch (IOException e) {
            r.errors.error(ErrorCategory.GENERATION, "CFG002", "UnreadableFile", e.getMessage(), SourcePosition.unknown(layout.appFile().toString()));
        }

        List<JinjaTemplate> templates = new ArrayList<>();
        for (Path file : layout.templateFiles()) {
            String name = file.getFileName().toString();
            try {
                SourceFile src = SourceFile.read(file, name);
                // قوالب جينجا تمر بمحلل لفظي مستقل يدرك حدود تعابير القالب ووسوم الواجهة.
                List<Token> tokens = new JinjaLexer(src).tokenize();
                r.log.info("Jinja lexer: " + tokens.size() + " tokens from " + name);
                // هنا يبني المحلل النحوي شجرة القالب من النص والعناصر والحلقات والشروط.
                RuleNode tree = new JinjaParser(tokens).parseTemplate();
                r.jinjaParseTrees.put(name, tree);
                // كل قالب يتحول إلى شجرة مجردة مستقلة قبل تجميع قوالب المشروع.
                JinjaTemplate template = new JinjaAstBuilder(name).build(tree);
                templates.add(template);
                r.log.info("Jinja AST built for " + name + ": parse tree " + tree.nodeCount() + " nodes -> AST " + template.nodeCount() + " nodes");
            } catch (CompilerException e) {
                r.errors.add(e.error());
                r.log.info("Jinja analysis failed for " + name + ": " + e.error().format());
            } catch (IOException e) {
                r.errors.error(ErrorCategory.GENERATION, "CFG002", "UnreadableFile", e.getMessage(), SourcePosition.unknown(name));
            }
        }
        r.jinjaAst = new JinjaProject(SourcePosition.unknown(layout.display(layout.templatesDir())), layout.display(layout.templatesDir()), templates);

        for (Path css : layout.cssFiles()) {
            String name = layout.display(css);
            try {
                SourceFile src = SourceFile.read(css, name);
                // يدعم المشروع تحليل ملف التنسيق أيضا: رموز ثم قواعد ثم شجرة مجردة لفحص البنية.
                List<Token> tokens = new CssLexer(src).tokenize();
                RuleNode tree = new CssParser(tokens).parseStylesheet();
                r.cssParseTrees.put(name, tree);
                Stylesheet sheet = new CssAstBuilder(name).build(tree);
                r.stylesheets.add(sheet);
                r.log.info("CSS AST built for " + name + ": " + tokens.size() + " tokens, parse tree " + tree.nodeCount() + " nodes -> AST " + sheet.nodeCount() + " nodes");
            } catch (CompilerException e) {
                r.errors.add(e.error());
                r.log.info("CSS analysis failed for " + name + ": " + e.error().format());
            } catch (IOException e) {
                r.errors.error(ErrorCategory.GENERATION, "CFG002", "UnreadableFile", e.getMessage(), SourcePosition.unknown(name));
            }
        }
        writeAstOutputs(r);

        if (r.errors.hasErrors()) {
            writeSemanticReport(r, "Semantic analysis was not reached because of lexical/syntax errors.");
            return abort(r, "lexical/syntax errors");
        }

        // ---------------------------------------------------- semantic
        r.stageReached = CompilationResult.Stage.SEMANTIC;
        r.log.section("Semantic analysis");
        // جدول الرموز ينشأ في مرحلة الفحص الدلالي للتحقق من التعريفات والنطاقات والأنواع.
        r.symbols = new SymbolTable();
        PythonSemanticAnalyzer pythonAnalyzer = new PythonSemanticAnalyzer(r.errors, r.symbols, layout.templateNames(), layout.staticNames());
        r.model = pythonAnalyzer.analyze(r.pythonAst);
        r.log.info("Python/Flask semantic analysis: " + r.model.routes().size() + " routes, " + r.model.renderCalls().size() + " render_template calls");
        JinjaSemanticAnalyzer jinjaAnalyzer = new JinjaSemanticAnalyzer(r.errors, r.symbols, r.model);
        HtmlValidator htmlValidator = new HtmlValidator(r.errors);
        for (JinjaTemplate t : templates) {
            jinjaAnalyzer.analyze(t);
            htmlValidator.validate(t);
        }
        r.log.info("Jinja/HTML semantic analysis: " + templates.size() + " templates");
        // الفحص العابر يربط استدعاءات عرض قوالب بايثون بالمتغيرات التي يحتاجها جينجا.
        new CrossLanguageAnalyzer(r.errors, r.model).analyze(r.jinjaAst);
        r.log.info("Cross-language analysis done");
        CssAnalyzer cssAnalyzer = new CssAnalyzer(r.errors);
        for (Stylesheet s : r.stylesheets) cssAnalyzer.analyze(s);
        if (!r.stylesheets.isEmpty()) r.log.info("CSS analysis: " + r.stylesheets.size() + " stylesheet(s)");
        // حفظ جدول الرموز تقرير للتوثيق والعرض، وليس تمريرا للجدول إلى مولد الصفحات.
        writeText(r, "symbol_table.txt", new SymbolTablePrinter(false).print(r.symbols));
        r.log.info("Semantic analysis finished: " + r.errors.errorCount() + " error(s), " + r.errors.warningCount() + " warning(s)");
        writeSemanticReport(r, null);

        if (r.errors.hasErrors()) {
            // هذا الشرط يوقف الانتقال إلى التوليد عند وجود أخطاء دلالية؛ تبقى التشخيصات محفوظة.
            return abort(r, "semantic errors");
        }
        r.log.info("Semantic analysis PASSED");

        if (request.checkOnly()) {
            r.success = true;
            r.log.section("Result");
            r.log.info("Check-only mode: code generation skipped by request");
            r.log.info("Compilation finished: SUCCESS (analysis only)");
            writeLog(r);
            return r;
        }

        // ------------------------------------------------ context data
        r.stageReached = CompilationResult.Stage.CONTEXT;
        r.log.section("Context / data preparation (Python AST -> Jinja context)");
        try {
            // بعد نجاح الفحص، يستخرج مكوّن السياق بيانات المنتجات والقيم من شجرة بايثون.
            r.contexts = new ContextPreparer(r.model).prepare(r.pythonAst);
        } catch (CompilerException e) {
            r.errors.add(e.error());
            writeSemanticReport(r, "Context preparation failed after semantic analysis passed.");
            return abort(r, "context preparation error");
        }
        for (String note : r.contexts.notes()) r.log.info(note);
        for (Map.Entry<String, Value> e : r.contexts.moduleData().entrySet()) {
            r.log.info("Module data '" + e.getKey() + "': " + e.getValue().typeName() + " " + summarize(e.getValue()));
        }
        for (PageContext p : r.contexts.pages()) r.log.info("Page context: " + p);
        writeContextData(r);

        // ------------------------------------------------- generation
        r.stageReached = CompilationResult.Stage.GENERATION;
        r.log.section("Code generation");
        try {
            // مدخلات التوليد: شجرة جينجا، والمسارات، والسياق المستخرج، والسجل؛ لا يدخل جدول الرموز هنا.
            CodeGenerator generator = new CodeGenerator(r.jinjaAst, r.model, r.contexts, r.log);
            r.generatedPages.addAll(generator.generate(r.outputDir));
            // تنسخ الملفات المساندة كما هي إلى مجلد التشغيل؛ التوليد الحقيقي يخص صفحات القوالب.
            r.copiedFiles.addAll(new FileCopier().copySupportFiles(layout, r.outputDir, r.log));
        } catch (CompilerException e) {
            r.errors.add(e.error());
            writeSemanticReport(r, "Code generation failed: " + e.error().format());
            return abort(r, "code generation error");
        } catch (IOException e) {
            r.errors.error(ErrorCategory.GENERATION, "GEN001", "IOError", e.getMessage(), SourcePosition.unknown(r.outputDir.toString()));
            return abort(r, "I/O error during generation");
        }

        r.stageReached = CompilationResult.Stage.DONE;
        r.success = true;
        r.log.section("Result");
        r.log.info("Generated files: " + r.generatedPages.size());
        r.log.info("Copied support files: " + r.copiedFiles.size());
        r.log.info("Compilation finished: SUCCESS");
        writeLog(r);
        return r;
    }

    // --------------------------------------------------------------- helpers

    private CompilationResult abort(CompilationResult r, String reason) {
        r.success = false;
        r.failureReason = reason;
        r.log.section("Result");
        r.log.info("CODE GENERATION ABORTED: " + reason + " (" + r.errors.errorCount() + " error(s))");
        for (fjc.common.CompilerError e : r.errors.sorted()) if (e.isError()) r.log.info("  " + e.format());
        r.log.info("No files were generated or overwritten in " + GenerationLog.display(r.outputDir));
        r.log.info("Compilation finished: FAILURE");
        writeLog(r);
        return r;
    }

    private void writeLog(CompilationResult r) {
        try {
            Path file = r.compilerOutputDir.resolve("generation_log.txt");
            r.log.writeTo(file);
            r.compilerOutputFiles.put("generation_log.txt", file);
        } catch (IOException e) {
            System.err.println("Cannot write generation log: " + e.getMessage());
        }
    }

    private void writeText(CompilationResult r, String name, String text) {
        try {
            Path directory = REQUIRED_REPORTS.contains(name) ? r.compilerOutputDir : detailsDir(r.compilerOutputDir);
            Files.createDirectories(directory);
            Path file = directory.resolve(name);
            Files.writeString(file, text, StandardCharsets.UTF_8);
            r.compilerOutputFiles.put(name, file);
            r.log.info("Wrote " + name);
        } catch (IOException e) {
            r.errors.error(ErrorCategory.GENERATION, "GEN001", "IOError", "Cannot write " + name + ": " + e.getMessage(),
                    SourcePosition.unknown(name));
        }
    }

    private void writeSemanticReport(CompilationResult r, String note) {
        List<String> files = new ArrayList<>();
        if (r.layout != null) {
            files.add(r.layout.display(r.layout.appFile()));
            files.addAll(r.layout.templateNames());
            for (Path css : r.layout.cssFiles()) files.add(r.layout.display(css));
        }
        String text = new SemanticReportWriter().write(r.errors, r.layout == null ? "?" : r.layout.name(), files);
        if (note != null) text += System.lineSeparator() + "Note: " + note + System.lineSeparator();
        writeText(r, "semantic_report.txt", text);
    }

    private void writeAstOutputs(CompilationResult r) {
        AstJsonSerializer json = new AstJsonSerializer();
        AstTreePrinter tree = new AstTreePrinter(false);
        ParseTreePrinter parseTree = new ParseTreePrinter(false);
        if (r.pythonAst != null) {
            writeText(r, "ast_python.json", json.toPrettyJson(r.pythonAst));
            writeText(r, "ast_python_tree.txt", tree.print(r.pythonAst));
            writeText(r, "parse_tree_python.txt", "Parse tree nodes: " + r.pythonParseTree.nodeCount()
                    + " (tokens: " + r.pythonParseTree.tokenCount() + "), AST nodes: " + r.pythonAst.nodeCount()
                    + System.lineSeparator() + parseTree.print(r.pythonParseTree));
        }
        if (r.jinjaAst != null) {
            writeText(r, "ast_jinja.json", json.toPrettyJson(r.jinjaAst));
            writeText(r, "ast_jinja_tree.txt", tree.print(r.jinjaAst));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, RuleNode> e : r.jinjaParseTrees.entrySet()) {
                JinjaTemplate t = r.jinjaAst.template(e.getKey());
                sb.append("==== ").append(e.getKey()).append(" : parse tree nodes ").append(e.getValue().nodeCount())
                        .append(", AST nodes ").append(t == null ? "?" : t.nodeCount()).append(System.lineSeparator());
                sb.append(parseTree.print(e.getValue())).append(System.lineSeparator());
            }
            writeText(r, "parse_tree_jinja.txt", sb.toString());
        }
        if (!r.stylesheets.isEmpty()) {
            JsonArray sheets = new JsonArray();
            StringBuilder trees = new StringBuilder();
            for (Stylesheet s : r.stylesheets) {
                sheets.add(json.toJson(s));
                trees.append(tree.print(s)).append(System.lineSeparator());
            }
            JsonObject root = new JsonObject();
            root.addProperty("type", "CssProject");
            root.add("children", sheets);
            writeText(r, "ast_css.json", new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
            writeText(r, "ast_css_tree.txt", trees.toString());
        }
    }

    private void writeContextData(CompilationResult r) {
        JsonObject root = new JsonObject();
        JsonObject module = new JsonObject();
        for (Map.Entry<String, Value> e : r.contexts.moduleData().entrySet()) {
            module.add(e.getKey(), new GsonBuilder().create().toJsonTree(e.getValue().toJava()));
        }
        root.add("moduleData", module);
        JsonArray pages = new JsonArray();
        for (PageContext p : r.contexts.pages()) {
            JsonObject o = new JsonObject();
            o.addProperty("endpoint", p.endpoint());
            o.addProperty("rule", p.route().rule());
            o.addProperty("template", p.templateName());
            o.addProperty("runtimeUrl", p.runtimeUrl());
            JsonObject params = new JsonObject();
            for (Map.Entry<String, Value> e : p.routeParameters().entrySet()) params.add(e.getKey(), new GsonBuilder().create().toJsonTree(e.getValue().toJava()));
            o.add("routeParameters", params);
            JsonObject ctx = new JsonObject();
            for (Map.Entry<String, Value> e : p.context().entrySet()) ctx.add(e.getKey(), new GsonBuilder().create().toJsonTree(e.getValue().toJava()));
            o.add("context", ctx);
            pages.add(o);
        }
        root.add("pages", pages);
        writeText(r, "context_data.json", new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(root));
    }

    private static String summarize(Value v) {
        String s = v.asText();
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
