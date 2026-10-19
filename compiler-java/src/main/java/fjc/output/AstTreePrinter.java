package fjc.output;

import fjc.ast.AstNode;

import java.util.List;

/**
 * Readable recursive tree printing for any AST (Python, Jinja, CSS). Relies
 * on the polymorphic {@link AstNode#label()} and {@link AstNode#children()}.
 *
 * <pre>
 * PythonProgram: file="app.py"
 * ├── Assignment: products
 * │   └── ListLiteral (2 elements)
 * │       ├── DictionaryLiteral [id, name, price]
 * ...
 * </pre>
 */
public final class AstTreePrinter {
    private final boolean ascii;
    private final boolean showPositions;

    public AstTreePrinter(boolean ascii) { this(ascii, true); }

    public AstTreePrinter(boolean ascii, boolean showPositions) {
        this.ascii = ascii;
        this.showPositions = showPositions;
    }

    /** Prints a node and all its descendants. */
    // يطبع الشجرة من الجذر باستدعاء تابع يزور الأبناء تكراريا مع الحفاظ على التفرعات.
    public String print(AstNode root) {
        StringBuilder sb = new StringBuilder();
        print(root, "", true, true, sb);
        return sb.toString();
    }

    /** Prints a single node line (no children). */
    public String printNode(AstNode node) {
        return node.label() + (showPositions ? "  (" + node.line() + ":" + node.column() + ")" : "");
    }

    private void print(AstNode node, String prefix, boolean last, boolean root, StringBuilder sb) {
        // يحدد شكل الفرع المرئي ويطبع اسم العقدة وموقعها قبل المرور على الأبناء.
        String branch = root ? "" : (last ? (ascii ? "`-- " : "└── ") : (ascii ? "|-- " : "├── "));
        sb.append(prefix).append(branch).append(printNode(node)).append('\n');
        List<? extends AstNode> children = node.children();
        String childPrefix = root ? "" : prefix + (last ? "    " : (ascii ? "|   " : "│   "));
        for (int i = 0; i < children.size(); i++) {
            print(children.get(i), childPrefix, i == children.size() - 1, false, sb);
        }
    }
}
