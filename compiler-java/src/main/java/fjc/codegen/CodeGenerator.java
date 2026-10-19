package fjc.codegen;

import fjc.evaluate.ContextData;
import fjc.evaluate.PageContext;
import fjc.jinja.ast.JinjaProject;
import fjc.jinja.ast.JinjaTemplate;
import fjc.output.GenerationLog;
import fjc.semantic.FlaskModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Code generation stage: turns the validated Jinja AST plus the prepared
 * context data into connected HTML pages.
 *
 * <p>Inputs are exactly the validated AST ({@link JinjaProject}), the route model
 * (endpoint → rule/methods, needed to resolve url_for) and the {@link ContextData}
 * produced by the evaluator from the Python AST. The symbol table is
 * deliberately not an input: it belongs to semantic analysis.
 */
public final class CodeGenerator {
    private final JinjaProject templates;
    private final FlaskModel model;
    private final ContextData contexts;
    private final GenerationLog log;

    public CodeGenerator(JinjaProject templates, FlaskModel model, ContextData contexts, GenerationLog log) {
        this.templates = templates;
        this.model = model;
        this.contexts = contexts;
        this.log = log;
    }

    /** Output file name for a template: index.jinja → index.html. */
    public static String outputFileName(String templateName) {
        String base = templateName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base + ".html";
    }

    /** endpoint → generated file, for every route that renders a template with at least one page instance. */
    public Map<String, String> pageFilesByEndpoint() {
        Map<String, String> files = new LinkedHashMap<>();
        for (PageContext page : contexts.pages()) {
            files.putIfAbsent(page.endpoint(), outputFileName(page.templateName()));
        }
        return files;
    }

    // توليد HTML من شجرة Jinja وسياقات الصفحات؛ جدول الرموز ليس مدخلاً لهذه المرحلة.
    // تبدأ مرحلة الكتابة بعد اجتياز التحليل الدلالي ومعالجة شجرة القالب باستخدام سياقات بايثون.
    public List<GeneratedPage> generate(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        UrlResolver urls = new UrlResolver(model, pageFilesByEndpoint());
        TemplateRenderer renderer = new TemplateRenderer(urls);
        List<GeneratedPage> generated = new ArrayList<>();

        log.info("Code generation started: output directory " + GenerationLog.display(outputDir));
        for (Map.Entry<String, String> e : urls.pageFiles().entrySet()) {
            log.info("Endpoint '" + e.getKey() + "' -> " + e.getValue());
        }
        for (JinjaTemplate template : templates.templates()) {
            // لكل قالب نجمع سياقات مساراته؛ قد تنتج صفحة التفاصيل مثيلات متعددة بحسب المنتجات.
            List<PageContext> pages = contexts.pagesFor(template.name());
            if (pages.isEmpty()) {
                // Remove an older generated page if its route no longer has
                // any instances (for example, after deleting the last product).
                // Otherwise the previous HTML remains accessible with stale data.
                Path stalePage = outputDir.resolve(outputFileName(template.name()));
                if (Files.deleteIfExists(stalePage)) {
                    log.info("Removed obsolete generated page " + stalePage.getFileName());
                }
                log.info("Template " + template.name() + ": no page instances (not rendered by any route, or no data) - skipped");
                continue;
            }
            // يُنتج عارض القوالب النص النهائي ويستبدل المتغيرات والحلقات والشروط بقيم السياق.
            String html = renderer.renderInstances(template, pages);
            // أدوات فحص المترجم تخص واجهة Flask الأصلية فقط، ولا تُعرض في صفحات HTML المولّدة.
            // نحذف روابطها من شريط التنقل في الخرج النهائي دون المساس بقوالب Jinja الأصلية.
            html = removeCompilerToolsFromGeneratedNavigation(html);
            String fileName = outputFileName(template.name());
            Path target = outputDir.resolve(fileName);
            // هذا هو أمر إنشاء ملف الصفحة النهائي على القرص بترميز يحافظ على النصوص.
            Files.writeString(target, html, StandardCharsets.UTF_8);
            GeneratedPage page = new GeneratedPage(template.name(), fileName, target, pages.size(), html.getBytes(StandardCharsets.UTF_8).length);
            generated.add(page);
            log.info("Generated " + page);
        }
        return generated;
    }

    // إزالة عناصر شريط الأدوات فقط؛ لا تغيير في تحليل القالب أو السياق أو وظائف المنتجات.
    private static String removeCompilerToolsFromGeneratedNavigation(String html) {
        return html
                .replace("<li><a href=\"/compiler/trees\">Compiler Trees</a></li>", "")
                .replace("<li><a href=\"/compiler/validate\">Compiler Validation</a></li>", "");
    }
}
