package fjc.jinja.ast;

/** Visitor over the Jinja/HTML AST (one method per concrete node class). */
// تعدد أشكال الزيارة: لكل نوع عقدة جينجا تابع زيارة مناسب دون تحويل كل العقد إلى نوع واحد.
public interface JinjaAstVisitor<R> {
    R visitProject(JinjaProject node);
    R visitTemplate(JinjaTemplate node);
    R visitText(TextNode node);
    R visitDoctype(HtmlDoctype node);
    R visitComment(HtmlComment node);
    R visitElement(HtmlElement node);
    R visitAttribute(HtmlAttribute node);
    R visitOutput(OutputExpression node);
    R visitFor(ForStatement node);
    R visitIf(IfStatement node);
    R visitIfBranch(IfBranch node);

    R visitVariable(VariableReference node);
    R visitPropertyAccess(PropertyAccess node);
    R visitIndexAccess(IndexAccess node);
    R visitFilter(FilterExpression node);
    R visitTest(TestExpression node);
    R visitFunctionCall(FunctionCall node);
    R visitUrlFor(UrlForExpression node);
    R visitLiteral(JinjaLiteral node);
    R visitListLiteral(JinjaListLiteral node);
    R visitBinary(JinjaBinaryExpression node);
    R visitComparison(JinjaComparisonExpression node);
    R visitLogical(JinjaLogicalExpression node);
    R visitUnary(JinjaUnaryExpression node);
    R visitKeywordArgument(JinjaKeywordArgument node);
}
