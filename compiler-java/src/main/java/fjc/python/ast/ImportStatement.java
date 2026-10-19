package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code import x.y as z} or {@code from module import a, b as c}.
 * Imported names become symbols of kind IMPORT in the module scope.
 */
public final class ImportStatement extends PythonStatement {
    private final String module;
    private final boolean fromImport;
    private final List<ImportedName> names;

    public ImportStatement(SourcePosition position, String module, boolean fromImport, List<ImportedName> names) {
        super(position);
        this.module = module;
        this.fromImport = fromImport;
        this.names = Collections.unmodifiableList(new ArrayList<>(names));
    }

    public String module() { return module; }
    public boolean isFromImport() { return fromImport; }
    public List<ImportedName> names() { return names; }

    @Override public String nodeName() { return "ImportStatement"; }
    @Override public List<ImportedName> children() { return names; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("module", module);
        p.put("from", fromImport);
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitImport(this); }
}
