package fjc.parsetree;

import java.util.List;

/** Readable recursive printing of a parse tree (every production and token). */
public final class ParseTreePrinter {
    private final boolean ascii;

    public ParseTreePrinter(boolean ascii) { this.ascii = ascii; }

    public String print(ParseTreeNode root) {
        StringBuilder sb = new StringBuilder();
        print(root, "", true, true, sb);
        return sb.toString();
    }

    private void print(ParseTreeNode node, String prefix, boolean last, boolean root, StringBuilder sb) {
        String branch = root ? "" : (last ? (ascii ? "`-- " : "└── ") : (ascii ? "|-- " : "├── "));
        sb.append(prefix).append(branch);
        if (node.isToken()) {
            TokenNode t = (TokenNode) node;
            sb.append(t.name()).append(" '").append(t.text().replace("\n", "\\n")).append("'");
        } else {
            sb.append(node.name());
        }
        sb.append("  (").append(node.position().line()).append(':').append(node.position().column()).append(")\n");
        List<ParseTreeNode> children = node.children();
        String childPrefix = root ? "" : prefix + (last ? "    " : (ascii ? "|   " : "│   "));
        for (int i = 0; i < children.size(); i++) {
            print(children.get(i), childPrefix, i == children.size() - 1, false, sb);
        }
    }
}
