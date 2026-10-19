package fjc.symbols;

import java.util.ArrayList;
import java.util.List;

/** Prints the scope tree with its symbols in a readable indented form. */
public final class SymbolTablePrinter {
    private final boolean ascii;

    public SymbolTablePrinter(boolean ascii) { this.ascii = ascii; }

    public String print(SymbolTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("SYMBOL TABLE\n");
        printScope(table.global(), "", true, true, sb);
        return sb.toString();
    }

    private void printScope(Scope scope, String prefix, boolean last, boolean root, StringBuilder sb) {
        String branch = root ? "" : (last ? (ascii ? "`-- " : "└── ") : (ascii ? "|-- " : "├── "));
        sb.append(prefix).append(branch).append("[scope ").append(scope.kind()).append("] ").append(scope.name()).append('\n');
        String childPrefix = root ? "" : prefix + (last ? "    " : (ascii ? "|   " : "│   "));
        List<Object> items = new ArrayList<>();
        items.addAll(scope.symbols());
        items.addAll(scope.children());
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            boolean isLast = i == items.size() - 1;
            if (item instanceof Symbol) {
                String b = isLast ? (ascii ? "`-- " : "└── ") : (ascii ? "|-- " : "├── ");
                sb.append(childPrefix).append(b).append(item).append('\n');
            } else {
                printScope((Scope) item, childPrefix, isLast, false, sb);
            }
        }
    }
}
