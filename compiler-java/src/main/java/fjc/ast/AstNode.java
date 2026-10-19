package fjc.ast;

import fjc.common.SourcePosition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root of every abstract syntax tree node (Python, Jinja and CSS trees).
 *
 * <p>Compared with the parse tree, an AST node is a <em>compressed semantic</em>
 * representation: it stores the meaning (a route rule, a literal value, a loop
 * variable) and no grammar-only detail (no parentheses, commas, colons,
 * delimiters or single-child wrapper productions).
 *
 * <p>Every node stores its source location (file, line, column) and exposes its
 * children polymorphically, which is what the tree printer and the JSON
 * serializer rely on. Concrete node classes add typed accessors and accept a
 * language specific visitor.
 */
// أصل مشترك لعقد الأشجار يعرّف الأبناء والخصائص والموقع ويتيح الوراثة وتعدد الأشكال.
// هذا الأصل المشترك يحقق الوراثة؛ عقد بايثون وجينجا والتنسيق تشترك في واجهة الأبناء والمعلومات.
public abstract class AstNode {
    private final SourcePosition position;

    protected AstNode(SourcePosition position) {
        this.position = position == null ? SourcePosition.unknown("<memory>") : position;
    }

    /** The node type name, e.g. "Assignment" or "ForStatement". */
    // تعدد الأشكال: كل صنف عقدة يحدد اسمه الحقيقي عبر إعادة تنفيذ هذا التابع.
    public abstract String nodeName();

    /** Child nodes in source order (never null). */
    // كل عقدة تعيد أبناءها بترتيبهم؛ عليه تعتمد طباعة الشجرة والتنقل داخلها.
    public abstract List<? extends AstNode> children();

    /**
     * Semantic properties of the node (name, value, route rule, ...), used by
     * the JSON serializer and the tree printer. Subclasses override.
     */
    public Map<String, Object> properties() {
        return new LinkedHashMap<>();
    }

    /** Short one-line label used by the tree printer, e.g. {@code Assignment: products}. */
    public String label() {
        Map<String, Object> props = properties();
        if (props.isEmpty()) return nodeName();
        StringBuilder sb = new StringBuilder(nodeName()).append(": ");
        boolean first = true;
        for (Map.Entry<String, Object> e : props.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append('=').append(formatValue(e.getValue()));
        }
        return sb.toString();
    }

    public final SourcePosition position() { return position; }
    // موقع العقدة مأخوذ من ملف المصدر، لذلك تعرض رسائل الأخطاء رقم السطر والعمود الصحيحين.
    public final int line() { return position.line(); }
    public final int column() { return position.column(); }
    public final String fileName() { return position.fileName(); }

    /** Number of nodes in this subtree (including this node). */
    public final int nodeCount() {
        int n = 1;
        for (AstNode c : children()) n += c.nodeCount();
        return n;
    }

    protected static String formatValue(Object v) {
        if (v == null) return "None";
        if (v instanceof String) {
            String s = (String) v;
            if (s.length() > 40) s = s.substring(0, 37) + "...";
            return "\"" + s.replace("\n", "\\n") + "\"";
        }
        return String.valueOf(v);
    }

    @Override
    public String toString() { return label() + " @" + position; }
}
