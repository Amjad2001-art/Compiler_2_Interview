package fjc.symbols;

import fjc.common.SourcePosition;
import fjc.semantic.ValueShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Hierarchical symbol table used by the semantic analysers.
 *
 * <p>Design note: the symbol table belongs to <em>semantic analysis</em>. The
 * code generator never touches it; it works on the validated AST and the
 * context data prepared by the evaluator (see {@code fjc.codegen}).
 */
/* جدول الرموز: فحص التعريفات والنطاقات والأنواع أثناء التحليل الدلالي فقط. */
public final class SymbolTable {
    private final Scope global;
    private final Deque<Scope> stack = new ArrayDeque<>();

    public SymbolTable() {
        this.global = new Scope("global", "global", null);
        stack.push(global);
    }

    public Scope global() { return global; }
    public Scope current() { return stack.peek(); }

    // عند دخول دالة أو مجال جديد ينشأ نطاق متفرع لتتبع تعريفات المتغيرات محليا.
    public Scope enter(String name, String kind) {
        Scope s = new Scope(name, kind, current());
        stack.push(s);
        return s;
    }

    /** Enters an already created scope again (e.g. to analyse a function body after declaring all functions). */
    public void reenter(Scope scope) {
        stack.push(scope);
    }

    public void exit() {
        if (stack.size() > 1) stack.pop();
    }

    // تعريف الرمز يسجل الاسم ونوعه وشكله وموقعه، ليستطيع المحلل الدلالي الرجوع إليه.
    public Symbol define(String name, SymbolKind kind, ValueShape shape, SourcePosition position, String detail) {
        Symbol symbol = new Symbol(name, kind, shape, position, detail);
        current().define(symbol);
        return symbol;
    }

    // البحث عن متغير يبدأ من النطاق الحالي ويتبع آلية النطاقات المعرفة؛ هذا يخدم كشف غير المعرف.
    public Symbol lookup(String name) { return current().lookup(name); }
    public Symbol lookupLocal(String name) { return current().lookupLocal(name); }

    /** Every symbol of every scope (depth first). */
    public List<Symbol> allSymbols() {
        List<Symbol> out = new ArrayList<>();
        collect(global, out);
        return out;
    }

    private static void collect(Scope scope, List<Symbol> out) {
        out.addAll(scope.symbols());
        for (Scope c : scope.children()) collect(c, out);
    }

    public List<Symbol> symbolsOfKind(SymbolKind kind) {
        List<Symbol> out = new ArrayList<>();
        for (Symbol s : allSymbols()) if (s.kind() == kind) out.add(s);
        return out;
    }

    public Scope findScope(String name) {
        return find(global, name);
    }

    private static Scope find(Scope scope, String name) {
        if (scope.name().equals(name)) return scope;
        for (Scope c : scope.children()) {
            Scope f = find(c, name);
            if (f != null) return f;
        }
        return null;
    }

    public String print() {
        return new SymbolTablePrinter(false).print(this);
    }
}
