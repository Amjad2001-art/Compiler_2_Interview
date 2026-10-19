package fjc.css;

import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic checks for stylesheets:
 * <ul>
 *   <li>CSS001 UnknownProperty – property name is not a known CSS property (vendor/custom allowed)</li>
 *   <li>CSS002 UndefinedCustomProperty – var(--x) but --x is never declared</li>
 *   <li>CSS003 DuplicateDeclaration – the same property twice in one block (warning)</li>
 *   <li>CSS004 EmptyRule – rule without declarations (warning)</li>
 *   <li>CSS005 InvalidColor – malformed hex colour such as #ggg or #12345</li>
 *   <li>CSS006 UnknownAtRule – unsupported at-rule</li>
 * </ul>
 */
// إجراء التحقق الدلالي لبنية CSS مع إبقاء ملف التنسيق منسوخاً دون تعديل.
public final class CssAnalyzer {

    static final Set<String> KNOWN_PROPERTIES = Set.of(
            "align-content", "align-items", "align-self", "all", "animation", "animation-delay", "animation-direction",
            "animation-duration", "animation-fill-mode", "animation-iteration-count", "animation-name",
            "animation-play-state", "animation-timing-function", "appearance", "aspect-ratio", "backdrop-filter",
            "backface-visibility", "background", "background-attachment", "background-blend-mode", "background-clip",
            "background-color", "background-image", "background-origin", "background-position", "background-repeat",
            "background-size", "border", "border-bottom", "border-bottom-color", "border-bottom-left-radius",
            "border-bottom-right-radius", "border-bottom-style", "border-bottom-width", "border-collapse", "border-color",
            "border-image", "border-left", "border-left-color", "border-left-style", "border-left-width", "border-radius",
            "border-right", "border-right-color", "border-right-style", "border-right-width", "border-spacing",
            "border-style", "border-top", "border-top-color", "border-top-left-radius", "border-top-right-radius",
            "border-top-style", "border-top-width", "border-width", "bottom", "box-shadow", "box-sizing", "break-after",
            "break-before", "break-inside", "caption-side", "caret-color", "clear", "clip", "clip-path", "color",
            "column-count", "column-gap", "column-rule", "column-span", "column-width", "columns", "contain", "content",
            "counter-increment", "counter-reset", "cursor", "direction", "display", "empty-cells", "filter", "flex",
            "flex-basis", "flex-direction", "flex-flow", "flex-grow", "flex-shrink", "flex-wrap", "float", "font",
            "font-family", "font-feature-settings", "font-kerning", "font-size", "font-size-adjust", "font-stretch",
            "font-style", "font-variant", "font-variant-numeric", "font-weight", "gap", "grid", "grid-area",
            "grid-auto-columns", "grid-auto-flow", "grid-auto-rows", "grid-column", "grid-column-end", "grid-column-gap",
            "grid-column-start", "grid-gap", "grid-row", "grid-row-end", "grid-row-gap", "grid-row-start",
            "grid-template", "grid-template-areas", "grid-template-columns", "grid-template-rows", "height", "hyphens",
            "inset", "isolation", "justify-content", "justify-items", "justify-self", "left", "letter-spacing",
            "line-break", "line-height", "list-style", "list-style-image", "list-style-position", "list-style-type",
            "margin", "margin-bottom", "margin-inline", "margin-block", "margin-left", "margin-right", "margin-top",
            "mask", "max-height", "max-width", "min-height", "min-width", "mix-blend-mode", "object-fit",
            "object-position", "opacity", "order", "outline", "outline-color", "outline-offset", "outline-style",
            "outline-width", "overflow", "overflow-wrap", "overflow-x", "overflow-y", "overscroll-behavior", "padding",
            "padding-bottom", "padding-inline", "padding-block", "padding-left", "padding-right", "padding-top",
            "page-break-after", "page-break-before", "page-break-inside", "perspective", "perspective-origin",
            "place-content", "place-items", "place-self", "pointer-events", "position", "quotes", "resize", "right",
            "row-gap", "scroll-behavior", "scroll-margin", "scroll-padding", "scroll-snap-type",
            "scrollbar-width", "scrollbar-color", "tab-size", "table-layout", "text-align", "text-align-last",
            "text-decoration", "text-decoration-color", "text-decoration-line", "text-decoration-style",
            "text-decoration-thickness", "text-indent", "text-justify", "text-overflow", "text-rendering", "text-shadow",
            "text-transform", "text-underline-offset", "top", "touch-action", "transform", "transform-origin",
            "transform-style", "transition", "transition-delay", "transition-duration", "transition-property",
            "transition-timing-function", "unicode-bidi", "user-select", "vertical-align", "visibility", "white-space",
            "widows", "width", "will-change", "word-break", "word-spacing", "word-wrap", "writing-mode", "z-index",
            "accent-color", "src", "font-display", "unicode-range", "inline-size", "block-size", "text-wrap",
            "translate", "rotate", "scale", "container-type", "inset-inline", "inset-block");

    private static final Set<String> KNOWN_AT_RULES = Set.of(
            "media", "keyframes", "-webkit-keyframes", "import", "font-face", "supports", "charset", "layer",
            "container", "page", "namespace");

    private final ErrorCollector errors;

    public CssAnalyzer(ErrorCollector errors) { this.errors = errors; }

    public void analyze(Stylesheet sheet) {
        Set<String> definedCustom = new HashSet<>();
        for (CssDeclaration d : sheet.allDeclarations()) if (d.isCustomProperty()) definedCustom.add(d.property());
        analyzeRules(sheet.rules(), definedCustom);
    }

    private void analyzeRules(List<? extends CssAstNode> rules, Set<String> definedCustom) {
        for (CssAstNode node : rules) {
            if (node instanceof CssRule) {
                CssRule rule = (CssRule) node;
                if (rule.declarations().isEmpty()) {
                    errors.warning(ErrorCategory.CSS, "CSS004", "EmptyRule",
                            "Rule '" + rule.selectorText() + "' has no declarations", rule.position());
                }
                analyzeDeclarations(rule.declarations(), definedCustom);
            } else if (node instanceof CssAtRule) {
                CssAtRule at = (CssAtRule) node;
                if (!KNOWN_AT_RULES.contains(at.name().toLowerCase())) {
                    errors.error(ErrorCategory.CSS, "CSS006", "UnknownAtRule",
                            "Unknown at-rule '@" + at.name() + "'", at.position());
                }
                analyzeRules(at.rules(), definedCustom);
                analyzeDeclarations(at.declarations(), definedCustom);
            }
        }
    }

    private void analyzeDeclarations(List<CssDeclaration> declarations, Set<String> definedCustom) {
        Map<String, CssDeclaration> seen = new HashMap<>();
        for (CssDeclaration d : declarations) {
            String prop = d.property().toLowerCase();
            if (seen.containsKey(prop)) {
                errors.warning(ErrorCategory.CSS, "CSS003", "DuplicateDeclaration",
                        "Property '" + d.property() + "' is declared twice in the same block (first at line "
                                + seen.get(prop).line() + ")", d.position());
            }
            seen.put(prop, d);
            if (!d.isCustomProperty() && !prop.startsWith("-") && !KNOWN_PROPERTIES.contains(prop)) {
                // يتحقق محلل التنسيق من أسماء الخصائص ويصدر خطأ عند استعمال خاصية غير مدعومة.
                errors.error(ErrorCategory.CSS, "CSS001", "UnknownProperty",
                        "Unknown CSS property '" + d.property() + "'", d.position());
            }
            List<String> toks = d.valueTokens();
            for (int i = 0; i < toks.size(); i++) {
                String t = toks.get(i);
                if (t.equals("var(") && i + 1 < toks.size()) {
                    String ref = toks.get(i + 1);
                    if (ref.startsWith("--") && !definedCustom.contains(ref)) {
                        errors.error(ErrorCategory.CSS, "CSS002", "UndefinedCustomProperty",
                                "var(" + ref + ") refers to a custom property that is never defined", d.position());
                    }
                }
                if (t.startsWith("#") && !d.isCustomProperty() && !isValidHexColor(t.substring(1))) {
                    errors.error(ErrorCategory.CSS, "CSS005", "InvalidColor",
                            "'" + t + "' is not a valid hex colour", d.position());
                }
            }
        }
    }

    static boolean isValidHexColor(String hex) {
        int n = hex.length();
        if (n != 3 && n != 4 && n != 6 && n != 8) return false;
        for (char c : hex.toCharArray()) if (Character.digit(c, 16) < 0) return false;
        return true;
    }
}
