package fjc.symbols;

import fjc.common.SourcePosition;
import fjc.semantic.ValueShape;

/** One entry of the symbol table: name, kind, inferred shape and where it was declared. */
public final class Symbol {
    private final String name;
    private final SymbolKind kind;
    private ValueShape shape;
    private final SourcePosition position;
    private final String detail;
    private int references = 0;

    public Symbol(String name, SymbolKind kind, ValueShape shape, SourcePosition position, String detail) {
        this.name = name;
        this.kind = kind;
        this.shape = shape == null ? ValueShape.ANY : shape;
        this.position = position;
        this.detail = detail;
    }

    public String name() { return name; }
    public SymbolKind kind() { return kind; }
    public ValueShape shape() { return shape; }
    public void setShape(ValueShape shape) { this.shape = shape == null ? ValueShape.ANY : shape; }
    public SourcePosition position() { return position; }
    public String detail() { return detail; }
    public int references() { return references; }
    public void markReferenced() { references++; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" : ").append(kind.name().toLowerCase().replace('_', ' '));
        sb.append(" [").append(shape.describe()).append("]");
        if (position != null && position.line() > 0) sb.append(" @ ").append(position.fileName()).append(':').append(position.line());
        if (detail != null && !detail.isEmpty()) sb.append("  -- ").append(detail);
        return sb.toString();
    }
}
