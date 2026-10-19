package fjc.evaluate;

import fjc.python.ast.FunctionDefinition;

/** A user-defined Python function (closure over the module environment). */
public final class FunctionValue extends Value {
    private final FunctionDefinition definition;
    private final Environment closure;

    public FunctionValue(FunctionDefinition definition, Environment closure) {
        this.definition = definition;
        this.closure = closure;
    }

    public FunctionDefinition definition() { return definition; }
    public Environment closure() { return closure; }

    @Override public String typeName() { return "function"; }
    @Override public boolean isTruthy() { return true; }
    @Override public String asText() { return "<function " + definition.name() + ">"; }
    @Override public Object toJava() { return asText(); }
}
