package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code {key: value, ...}} */
public final class DictionaryLiteral extends PythonExpression {
    private final List<DictionaryEntry> entries;

    public DictionaryLiteral(SourcePosition position, List<DictionaryEntry> entries) {
        super(position);
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<DictionaryEntry> entries() { return entries; }

    /** Entry whose key is the given string literal, or null. */
    public DictionaryEntry entry(String key) {
        for (DictionaryEntry e : entries) if (key.equals(e.keyName())) return e;
        return null;
    }

    public List<String> keyNames() {
        List<String> out = new ArrayList<>();
        for (DictionaryEntry e : entries) if (e.keyName() != null) out.add(e.keyName());
        return out;
    }

    @Override public String nodeName() { return "DictionaryLiteral"; }
    @Override public List<DictionaryEntry> children() { return entries; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("keys", keyNames());
        return p;
    }
    @Override public String label() { return "DictionaryLiteral " + keyNames(); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitDictionaryLiteral(this); }
}
