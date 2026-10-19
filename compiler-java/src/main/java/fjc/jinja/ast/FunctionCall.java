package fjc.jinja.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A generic call {@code callee(args, k=v)} (url_for has its own node). */
public final class FunctionCall extends JinjaExpression {
    private final JinjaExpression callee;
    private final List<JinjaExpression> arguments;
    private final List<JinjaKeywordArgument> keywordArguments;

    public FunctionCall(SourcePosition position, JinjaExpression callee, List<JinjaExpression> arguments,
                        List<JinjaKeywordArgument> keywordArguments) {
        super(position);
        this.callee = callee;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.keywordArguments = Collections.unmodifiableList(new ArrayList<>(keywordArguments));
    }

    public JinjaExpression callee() { return callee; }
    public List<JinjaExpression> arguments() { return arguments; }
    public List<JinjaKeywordArgument> keywordArguments() { return keywordArguments; }

    public String calleeName() {
        return callee instanceof VariableReference ? ((VariableReference) callee).name()
                : callee instanceof PropertyAccess ? ((PropertyAccess) callee).dottedName() : null;
    }

    @Override public String nodeName() { return "FunctionCall"; }
    @Override public List<JinjaAstNode> children() {
        List<JinjaAstNode> out = new ArrayList<>();
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
    @Override public String label() { return "FunctionCall: " + (calleeName() != null ? calleeName() : "<expr>") + "()"; }
    @Override public <R> R accept(JinjaAstVisitor<R> visitor) { return visitor.visitFunctionCall(this); }
}
