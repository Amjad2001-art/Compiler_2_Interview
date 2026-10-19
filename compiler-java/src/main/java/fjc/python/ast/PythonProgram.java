package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Root of the Python AST: one module (e.g. app.py). */
public final class PythonProgram extends PythonAstNode {
    private final String fileName;
    private final List<PythonStatement> statements;

    public PythonProgram(SourcePosition position, String fileName, List<PythonStatement> statements) {
        super(position);
        this.fileName = fileName;
        this.statements = Collections.unmodifiableList(new ArrayList<>(statements));
    }

    public String moduleFileName() { return fileName; }
    public List<PythonStatement> statements() { return statements; }

    /** All route definitions in the module, in source order. */
    public List<RouteDefinition> routes() {
        List<RouteDefinition> out = new ArrayList<>();
        for (PythonStatement s : statements) if (s instanceof RouteDefinition) out.add((RouteDefinition) s);
        return out;
    }

    /** All plain (non-route) function definitions. */
    public List<FunctionDefinition> functions() {
        List<FunctionDefinition> out = new ArrayList<>();
        for (PythonStatement s : statements) if (s instanceof FunctionDefinition) out.add((FunctionDefinition) s);
        return out;
    }

    @Override public String nodeName() { return "PythonProgram"; }
    @Override public List<PythonStatement> children() { return statements; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("file", fileName);
        return p;
    }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitProgram(this); }
}
