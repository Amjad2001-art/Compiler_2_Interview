package fjc.python.ast;

import fjc.common.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code render_template("name.jinja", var=value, ...)}.
 * A Flask-aware call node: the template name is stored as a property and the
 * keyword arguments are the context variables handed to the Jinja template.
 */
public final class RenderTemplateCall extends PythonExpression {
    private final String templateName;
    private final SourcePosition templateNamePosition;
    private final List<KeywordArgument> contextArguments;

    public RenderTemplateCall(SourcePosition position, String templateName, SourcePosition templateNamePosition,
                              List<KeywordArgument> contextArguments) {
        super(position);
        this.templateName = templateName;
        this.templateNamePosition = templateNamePosition;
        this.contextArguments = Collections.unmodifiableList(new ArrayList<>(contextArguments));
    }

    public String templateName() { return templateName; }
    public SourcePosition templateNamePosition() { return templateNamePosition; }
    public List<KeywordArgument> contextArguments() { return contextArguments; }

    public List<String> contextNames() {
        List<String> out = new ArrayList<>();
        for (KeywordArgument k : contextArguments) out.add(k.name());
        return out;
    }

    public KeywordArgument contextArgument(String name) {
        for (KeywordArgument k : contextArguments) if (k.name().equals(name)) return k;
        return null;
    }

    @Override public String nodeName() { return "RenderTemplateCall"; }
    @Override public List<KeywordArgument> children() { return contextArguments; }
    @Override public Map<String, Object> properties() {
        Map<String, Object> p = super.properties();
        p.put("template", templateName);
        p.put("context", contextNames());
        return p;
    }
    @Override public String label() { return "RenderTemplateCall: " + templateName + " " + contextNames(); }
    @Override public <R> R accept(PythonAstVisitor<R> visitor) { return visitor.visitRenderTemplateCall(this); }
}
