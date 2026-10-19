package fjc.output;

import fjc.common.CompilerError;
import fjc.common.ErrorCategory;
import fjc.common.ErrorCollector;

import java.util.List;

/** Produces compiler_output/semantic_report.txt. */
public final class SemanticReportWriter {

    public String write(ErrorCollector errors, String projectName, List<String> analyzedFiles) {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("SEMANTIC ANALYSIS REPORT").append(nl);
        sb.append("Project: ").append(projectName).append(nl);
        sb.append("Analyzed files: ").append(String.join(", ", analyzedFiles)).append(nl);
        sb.append("Format: [CODE] SEVERITY file:line:column Name: message").append(nl);
        sb.append(nl);

        long errorCount = errors.errorCount();
        long warningCount = errors.warningCount();
        for (ErrorCategory category : ErrorCategory.values()) {
            List<CompilerError> list = errors.byCategory(category);
            if (category == ErrorCategory.LEXICAL || category == ErrorCategory.SYNTAX || category == ErrorCategory.GENERATION) {
                if (list.isEmpty()) continue;
            }
            long errs = list.stream().filter(CompilerError::isError).count();
            sb.append("== ").append(category.title()).append(" (").append(errs).append(" error(s), ")
                    .append(list.size() - errs).append(" warning(s)) ==").append(nl);
            if (list.isEmpty()) {
                sb.append("  none").append(nl);
            }
            for (CompilerError e : errors.sorted()) {
                if (e.category() != category) continue;
                sb.append("  ").append(e.format()).append(nl);
            }
            sb.append(nl);
        }

        sb.append("== Summary ==").append(nl);
        sb.append("  Errors:   ").append(errorCount).append(nl);
        sb.append("  Warnings: ").append(warningCount).append(nl);
        if (errorCount == 0) {
            sb.append("  RESULT: SEMANTIC ANALYSIS PASSED - code generation allowed").append(nl);
        } else {
            sb.append("  RESULT: SEMANTIC ANALYSIS FAILED - code generation blocked").append(nl);
        }
        return sb.toString();
    }
}
