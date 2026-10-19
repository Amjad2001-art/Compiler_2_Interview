package fjc.semantic;

import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;
import fjc.jinja.ast.ForStatement;
import fjc.jinja.ast.HtmlAttribute;
import fjc.jinja.ast.HtmlElement;
import fjc.jinja.ast.IfBranch;
import fjc.jinja.ast.IfStatement;
import fjc.jinja.ast.JinjaAstNode;
import fjc.jinja.ast.JinjaTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTML-level semantic checks on the Jinja AST:
 * <ul>
 *   <li>HT001 UnknownElement – tag name is not an HTML element (custom elements with '-' are allowed)</li>
 *   <li>HT002 DuplicateAttribute – the same attribute twice on one element</li>
 *   <li>HT003 ImageWithoutAlt – img without alt (warning)</li>
 *   <li>HT004 NestedForm – form inside form</li>
 *   <li>HT005 DuplicateId – the same static id twice in a template</li>
 *   <li>HT006 StaticIdInsideLoop – a static id inside a loop repeats at runtime (warning)</li>
 *   <li>HT007 MissingRequiredAttribute – img/link without src/href, form without action/method (warning)</li>
 * </ul>
 */
public final class HtmlValidator {

    static final Set<String> KNOWN_ELEMENTS = Set.of(
            "a", "abbr", "address", "area", "article", "aside", "audio", "b", "base", "bdi", "bdo", "blockquote", "body",
            "br", "button", "canvas", "caption", "cite", "code", "col", "colgroup", "data", "datalist", "dd", "del",
            "details", "dfn", "dialog", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure", "footer",
            "form", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe", "img",
            "input", "ins", "kbd", "label", "legend", "li", "link", "main", "map", "mark", "menu", "meta", "meter", "nav",
            "noscript", "object", "ol", "optgroup", "option", "output", "p", "param", "picture", "pre", "progress", "q",
            "rp", "rt", "ruby", "s", "samp", "script", "section", "select", "slot", "small", "source", "span", "strong",
            "style", "sub", "summary", "sup", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead",
            "time", "title", "tr", "track", "u", "ul", "var", "video", "wbr", "svg", "path", "circle", "rect", "line",
            "polyline", "polygon", "g", "defs", "use", "symbol", "text");

    private final ErrorCollector errors;
    private Map<String, HtmlElement> ids;

    public HtmlValidator(ErrorCollector errors) { this.errors = errors; }

    public void validate(JinjaTemplate template) {
        ids = new HashMap<>();
        walk(template.children(), 0, 0);
    }

    private void walk(List<? extends JinjaAstNode> nodes, int formDepth, int loopDepth) {
        for (JinjaAstNode n : nodes) {
            if (n instanceof HtmlElement) {
                HtmlElement e = (HtmlElement) n;
                checkElement(e, formDepth, loopDepth);
                int nextForm = e.tagName().equals("form") ? formDepth + 1 : formDepth;
                walkAttributes(e.attributes(), loopDepth);
                walk(e.body(), nextForm, loopDepth);
            } else if (n instanceof ForStatement) {
                walk(((ForStatement) n).body(), formDepth, loopDepth + 1);
            } else if (n instanceof IfStatement) {
                IfStatement i = (IfStatement) n;
                for (IfBranch b : i.branches()) walk(b.body(), formDepth, loopDepth);
                walk(i.elseBody(), formDepth, loopDepth);
            }
        }
    }

    private void walkAttributes(List<JinjaAstNode> attributes, int loopDepth) {
        for (JinjaAstNode a : attributes) {
            if (a instanceof IfStatement) {
                IfStatement i = (IfStatement) a;
                for (IfBranch b : i.branches()) walkAttributes(b.body(), loopDepth);
                walkAttributes(i.elseBody(), loopDepth);
            } else if (a instanceof ForStatement) {
                walkAttributes(((ForStatement) a).body(), loopDepth + 1);
            }
        }
    }

    private void checkElement(HtmlElement e, int formDepth, int loopDepth) {
        String tag = e.tagName();
        if (!KNOWN_ELEMENTS.contains(tag) && !tag.contains("-")) {
            // التحقق الدلالي لعناصر الواجهة يرفض وسما غير معروف من وسوم اللغة المدعومة.
            errors.error(ErrorCategory.HTML, "HT001", "UnknownElement",
                    "<" + tag + "> is not a known HTML element", e.position());
        }
        Set<String> seen = new HashSet<>();
        for (HtmlAttribute a : e.plainAttributes()) {
            String name = a.name().toLowerCase();
            if (!seen.add(name)) {
                errors.error(ErrorCategory.HTML, "HT002", "DuplicateAttribute",
                        "Attribute '" + a.name() + "' appears more than once on <" + tag + ">", a.position());
            }
        }
        if (tag.equals("form") && formDepth > 0) {
            errors.error(ErrorCategory.HTML, "HT004", "NestedForm", "<form> elements cannot be nested", e.position());
        }
        HtmlAttribute id = e.attribute("id");
        if (id != null && id.staticValue() != null && !id.staticValue().isEmpty()) {
            String value = id.staticValue();
            if (loopDepth > 0) {
                errors.warning(ErrorCategory.HTML, "HT006", "StaticIdInsideLoop",
                        "Static id=\"" + value + "\" inside a '{% for %}' loop produces duplicate ids at runtime", id.position());
            } else if (ids.containsKey(value)) {
                errors.error(ErrorCategory.HTML, "HT005", "DuplicateId",
                        "id=\"" + value + "\" is already used by <" + ids.get(value).tagName() + "> at line "
                                + ids.get(value).line(), id.position());
            } else {
                ids.put(value, e);
            }
        }
        switch (tag) {
            case "img":
                if (e.attribute("src") == null) requiredMissing(e, "src");
                if (e.attribute("alt") == null) {
                    errors.warning(ErrorCategory.HTML, "HT003", "ImageWithoutAlt",
                            "<img> should have an alt attribute for accessibility", e.position());
                }
                break;
            case "link":
                if (e.attribute("href") == null) requiredMissing(e, "href");
                break;
            case "a":
                if (e.attribute("href") == null) requiredMissing(e, "href");
                break;
            case "form":
                if (e.attribute("action") == null) requiredMissing(e, "action");
                if (e.attribute("method") == null) requiredMissing(e, "method");
                break;
            case "script":
                break;
            default:
                break;
        }
    }

    private void requiredMissing(HtmlElement e, String attribute) {
        errors.warning(ErrorCategory.HTML, "HT007", "MissingRequiredAttribute",
                "<" + e.tagName() + "> is missing the '" + attribute + "' attribute", e.position());
    }
}
