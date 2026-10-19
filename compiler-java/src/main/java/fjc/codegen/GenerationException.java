package fjc.codegen;

import fjc.common.CompilerError;
import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourcePosition;

/** Raised by the template renderer / generator; reported as a GENERATION error. */
public final class GenerationException extends CompilerException {
    public GenerationException(String code, String name, String message, SourcePosition position) {
        super(CompilerError.error(ErrorCategory.GENERATION, code, name, message, position));
    }
}
