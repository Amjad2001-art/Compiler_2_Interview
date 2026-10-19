package fjc.jinja.ast;

import fjc.common.SourcePosition;

/** Marker base class for Jinja expression nodes. */
public abstract class JinjaExpression extends JinjaAstNode {
    protected JinjaExpression(SourcePosition position) { super(position); }
}
