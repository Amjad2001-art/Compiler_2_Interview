package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** {@code callee(arg, ..., name=value, ...)} */
public class CallExpression extends PythonExpression {
    private final PythonExpression callee;
    private final List<PythonExpression> arguments;
    private final List<KeywordArgument> keywordArguments;

    public CallExpression(SourcePosition position, PythonExpression callee,
                          List<PythonExpression> arguments, List<KeywordArgument> keywordArguments) {
        super(position);
        this.callee = callee;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.keywordArguments = Collections.unmodifiableList(new ArrayList<>(keywordArguments));
    }

    public PythonExpression callee() { return callee; }
    public List<PythonExpression> arguments() { return arguments; }
    public List<KeywordArgument> keywordArguments() { return keywordArguments; }

    public KeywordArgument keyword(String name) {
        for (KeywordArgument k : keywordArguments) if (k.name().equals(name)) return k;
        return null;
    }

    /** Callee text when it is a simple or dotted name (e.g. {@code render_template}, {@code app.route}). */
    public String calleeName() {
        if (callee instanceof Identifier) return ((Identifier) callee).name();
        if (callee instanceof AttributeAccess) return ((AttributeAccess) callee).dottedName();
        return null;
    }

    @Override public String nodeName() { return "CallExpression"; }
    @Override public List<PythonAstNode> children() {
        List<PythonAstNode> out = new ArrayList<>();
        out.add(callee);
        out.addAll(arguments);
        out.addAll(keywordArguments);
        return out;
    }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        if (calleeName() != null) p.put("callee", calleeName());
        return p;
    }
    @Override public String label() { return "CallExpression: " + (calleeName() != null ? calleeName() : "<expr>") + "()"; }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitCall(this); }
}
