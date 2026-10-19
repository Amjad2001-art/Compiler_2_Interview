package fjc.evaluate;

import fjc.common.CompilerError;
import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourcePosition;

/** Raised when the compile-time evaluator cannot execute a construct. Reported as a GENERATION error. */
public final class EvaluationException extends CompilerException {
    public EvaluationException(String code, String name, String message, SourcePosition position) {
        super(CompilerError.error(ErrorCategory.GENERATION, code, name, message, position));
    }
}
