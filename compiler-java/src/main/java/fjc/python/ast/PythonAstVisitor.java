package fjc.python.ast;

/** Visitor over the Python AST (one method per concrete node class). */
// واجهة الزائر تعرف عملية مستقلة لكل نوع عقدة؛ الباني ينشئ العقد والزائر يحدد ما نفعله بها.
public interface PythonAstVisitor<R> {
    R visitProgram(PythonProgram node);
    R visitImport(ImportStatement node);
    R visitAssignment(Assignment node);
    R visitFunctionDefinition(FunctionDefinition node);
    R visitRouteDefinition(RouteDefinition node);
    R visitReturn(ReturnStatement node);
    R visitIf(IfStatement node);
    R visitFor(ForStatement node);
    R visitExpressionStatement(ExpressionStatement node);
    R visitPass(PassStatement node);
    R visitParameter(Parameter node);
    R visitConditionalBranch(ConditionalBranch node);
    R visitImportedName(ImportedName node);

    R visitIdentifier(Identifier node);
    R visitLiteral(Literal node);
    R visitListLiteral(ListLiteral node);
    R visitDictionaryLiteral(DictionaryLiteral node);
    R visitDictionaryEntry(DictionaryEntry node);
    R visitAttributeAccess(AttributeAccess node);
    R visitSubscriptAccess(SubscriptAccess node);
    R visitCall(CallExpression node);
    R visitKeywordArgument(KeywordArgument node);
    R visitRenderTemplateCall(RenderTemplateCall node);
    R visitBinary(BinaryExpression node);
    R visitComparison(ComparisonExpression node);
    R visitLogical(LogicalExpression node);
    R visitUnary(UnaryExpression node);
}
