package fjc.symbols;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A lexical scope (module, function, template, loop...) holding symbols and child scopes. */
public final class Scope {
    private final String name;
    private final String kind;
    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final List<Scope> children = new ArrayList<>();

    public Scope(String name, String kind, Scope parent) {
        this.name = name;
        this.kind = kind;
        this.parent = parent;
        if (parent != null) parent.children.add(this);
    }

    public String name() { return name; }
    public String kind() { return kind; }
    public Scope parent() { return parent; }
    public List<Scope> children() { return Collections.unmodifiableList(children); }
    public Collection<Symbol> symbols() { return Collections.unmodifiableCollection(symbols.values()); }

    /** Defines a symbol in this scope; returns the previous definition (or null) so callers can report duplicates. */
    public Symbol define(Symbol symbol) {
        return symbols.put(symbol.name(), symbol);
    }

    public Symbol lookupLocal(String name) { return symbols.get(name); }

    /** Walks this scope and its ancestors. */
    public Symbol lookup(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            Symbol sym = s.symbols.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    /** The scope that owns the symbol, or null. */
    public Scope owner(String name) {
        for (Scope s = this; s != null; s = s.parent) if (s.symbols.containsKey(name)) return s;
        return null;
    }

    public boolean isDescendantOf(Scope other) {
        for (Scope s = this; s != null; s = s.parent) if (s == other) return true;
        return false;
    }

    public String path() {
        return parent == null ? name : parent.path() + " > " + name;
    }

    @Override public String toString() { return kind + " " + name; }
}
