package fjc.python.parser;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourcePosition;
import fjc.common.Token;
import fjc.parsetree.RuleNode;
import fjc.python.lexer.PythonTokenType;

import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for the supported Python subset. It produces a
 * concrete parse tree ({@link RuleNode}/TokenNode) that keeps every grammar
 * production and every token; the {@code PythonAstBuilder} compresses it.
 *
 * <pre>
 * module              := (NEWLINE | statement)* EOF
 * statement           := simple_statement | compound_statement
 * simple_statement    := small_statement NEWLINE
 * small_statement     := import_statement | from_import_statement | return_statement
 *                      | pass_statement | assignment_statement | augmented_assignment
 *                      | expression_statement
 * import_statement    := 'import' dotted_name ('as' NAME)? (',' dotted_name ('as' NAME)?)*
 * from_import_statement := 'from' dotted_name 'import' import_name (',' import_name)*
 *                      | 'from' dotted_name 'import' '(' import_name (',' import_name)* ','? ')'
 * import_name         := NAME ('as' NAME)?
 * return_statement    := 'return' expression?
 * pass_statement      := 'pass'
 * assignment_statement:= expression '=' expression
 * augmented_assignment:= expression ('+=' | '-=' | '*=' | '/=') expression
 * expression_statement:= expression
 * compound_statement  := decorated_definition | function_definition | if_statement | for_statement
 * decorated_definition:= decorator+ function_definition
 * decorator           := '@' expression NEWLINE
 * function_definition := 'def' NAME '(' parameter_list? ')' ':' block
 * parameter_list      := parameter (',' parameter)* ','?
 * parameter           := NAME ('=' expression)?
 * if_statement        := 'if' expression ':' block elif_clause* else_clause?
 * elif_clause         := 'elif' expression ':' block
 * else_clause         := 'else' ':' block
 * for_statement       := 'for' target_list 'in' expression ':' block
 * target_list         := NAME (',' NAME)*
 * block               := NEWLINE INDENT statement+ DEDENT
 * expression          := or_test
 * or_test             := and_test ('or' and_test)*
 * and_test            := not_test ('and' not_test)*
 * not_test            := 'not' not_test | comparison
 * comparison          := arith_expr (comparison_operator arith_expr)*
 * comparison_operator := '==' | '!=' | '<' | '>' | '<=' | '>=' | 'in' | 'not' 'in' | 'is' | 'is' 'not'
 * arith_expr          := term (('+' | '-') term)*
 * term                := factor (('*' | '/' | '//' | '%') factor)*
 * factor              := ('-' | '+') factor | primary
 * primary             := atom trailer*
 * trailer             := '.' NAME | '(' argument_list? ')' | '[' expression ']'
 * argument_list       := argument (',' argument)* ','?
 * argument            := NAME '=' expression | expression
 * atom                := NAME | NUMBER | STRING+ | 'True' | 'False' | 'None'
 *                      | list_display | dict_display | '(' expression ')'
 * list_display        := '[' (expression (',' expression)* ','?)? ']'
 * dict_display        := '{' (dict_entry (',' dict_entry)* ','?)? '}'
 * dict_entry          := expression ':' expression
 * </pre>
 */
public final class PythonParser {

    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
            "class", "while", "try", "except", "finally", "with", "lambda", "yield", "raise",
            "global", "nonlocal", "del", "assert", "async", "await", "break", "continue");

    private final List<Token> tokens;
    private int index = 0;

    public PythonParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ------------------------------------------------------------ token helpers

    private Token peek() { return tokens.get(index); }
    private Token peek(int ahead) { return tokens.get(Math.min(index + ahead, tokens.size() - 1)); }
    private boolean at(PythonTokenType type) { return peek().is(type); }
    private boolean at(PythonTokenType type, String text) { return peek().is(type, text); }
    private boolean atOp(String text) { return at(PythonTokenType.OP, text); }
    private boolean atKeyword(String text) { return at(PythonTokenType.KEYWORD, text); }

    private Token next() {
        Token t = peek();
        if (!t.is(PythonTokenType.EOF)) index++;
        return t;
    }

    private CompilerException syntaxError(String message) {
        return syntaxError(message, peek());
    }

    private CompilerException syntaxError(String message, Token at) {
        return new CompilerException(ErrorCategory.SYNTAX, "SYN001", "PythonSyntaxError", message, at.position());
    }

    private String describe(Token t) {
        switch ((PythonTokenType) t.type()) {
            case NEWLINE: return "end of line";
            case INDENT: return "indent";
            case DEDENT: return "dedent";
            case EOF: return "end of file";
            case STRING: return "string literal";
            default: return "'" + t.text() + "'";
        }
    }

    private Token expectOp(String text) {
        if (!atOp(text)) throw syntaxError("Expected '" + text + "' but found " + describe(peek()));
        return next();
    }

    private Token expectKeyword(String text) {
        if (!atKeyword(text)) throw syntaxError("Expected '" + text + "' but found " + describe(peek()));
        return next();
    }

    private Token expect(PythonTokenType type, String what) {
        if (!at(type)) throw syntaxError("Expected " + what + " but found " + describe(peek()));
        return next();
    }

    private RuleNode rule(String name) {
        return new RuleNode(name, peek().position());
    }

    private RuleNode rule(String name, SourcePosition at) {
        return new RuleNode(name, at);
    }

    // ------------------------------------------------------------------ module

    // جذر القواعد النحوية يقرأ تعليمات الوحدة وينشئ شجرة تحليل؛ خطأ الصياغة يوقف هذه المرحلة.
    public RuleNode parseModule() {
        RuleNode module = rule("module");
        while (!at(PythonTokenType.EOF)) {
            if (at(PythonTokenType.NEWLINE)) { module.addToken(next()); continue; }
            if (at(PythonTokenType.INDENT)) throw syntaxError("Unexpected indentation");
            module.add(parseStatement());
        }
        module.addToken(next());
        return module;
    }

    // اختيار قاعدة التعليمة يعتمد على الرموز المقبلة: دالة أو حلقة أو شرط أو تعليمة بسيطة.
    private RuleNode parseStatement() {
        RuleNode stmt = rule("statement");
        Token t = peek();
        if (t.is(PythonTokenType.KEYWORD)) {
            String kw = t.text();
            if (UNSUPPORTED_KEYWORDS.contains(kw)) {
                throw new CompilerException(ErrorCategory.SYNTAX, "SYN002", "UnsupportedConstruct",
                        "The '" + kw + "' statement is not part of the supported Python subset", t.position());
            }
            switch (kw) {
                case "def": stmt.add(parseFunctionDefinition()); return stmt;
                case "if": stmt.add(parseIfStatement()); return stmt;
                case "for": stmt.add(parseForStatement()); return stmt;
                default: break;
            }
        }
        if (atOp("@")) { stmt.add(parseDecoratedDefinition()); return stmt; }
        stmt.add(parseSimpleStatement());
        return stmt;
    }

    private RuleNode parseSimpleStatement() {
        RuleNode simple = rule("simple_statement");
        simple.add(parseSmallStatement());
        if (at(PythonTokenType.NEWLINE)) simple.addToken(next());
        else if (!at(PythonTokenType.EOF)) throw syntaxError("Expected end of line but found " + describe(peek()));
        return simple;
    }

    private RuleNode parseSmallStatement() {
        if (atKeyword("import")) return parseImportStatement();
        if (atKeyword("from")) return parseFromImportStatement();
        if (atKeyword("return")) {
            RuleNode r = rule("return_statement");
            r.addToken(next());
            if (!at(PythonTokenType.NEWLINE) && !at(PythonTokenType.EOF)) r.add(parseExpression());
            return r;
        }
        if (atKeyword("pass")) {
            RuleNode r = rule("pass_statement");
            r.addToken(next());
            return r;
        }
        SourcePosition at = peek().position();
        RuleNode expr = parseExpression();
        if (atOp("=")) {
            RuleNode assign = rule("assignment_statement", at);
            assign.add(expr);
            assign.addToken(next());
            assign.add(parseExpression());
            if (atOp("=")) throw syntaxError("Chained assignment is not supported");
            return assign;
        }
        if (atOp("+=") || atOp("-=") || atOp("*=") || atOp("/=")) {
            RuleNode assign = rule("augmented_assignment", at);
            assign.add(expr);
            assign.addToken(next());
            assign.add(parseExpression());
            return assign;
        }
        RuleNode es = rule("expression_statement", at);
        es.add(expr);
        return es;
    }

    private RuleNode parseImportStatement() {
        RuleNode r = rule("import_statement");
        r.addToken(expectKeyword("import"));
        r.add(parseDottedName());
        if (atKeyword("as")) { r.addToken(next()); r.addToken(expect(PythonTokenType.NAME, "a name")); }
        while (atOp(",")) {
            r.addToken(next());
            r.add(parseDottedName());
            if (atKeyword("as")) { r.addToken(next()); r.addToken(expect(PythonTokenType.NAME, "a name")); }
        }
        return r;
    }

    private RuleNode parseFromImportStatement() {
        RuleNode r = rule("from_import_statement");
        r.addToken(expectKeyword("from"));
        r.add(parseDottedName());
        r.addToken(expectKeyword("import"));
        boolean paren = false;
        if (atOp("(")) { r.addToken(next()); paren = true; }
        if (atOp("*")) throw syntaxError("Wildcard imports ('from x import *') are not supported");
        r.add(parseImportName());
        while (atOp(",")) {
            r.addToken(next());
            if (paren && atOp(")")) break;
            r.add(parseImportName());
        }
        if (paren) r.addToken(expectOp(")"));
        return r;
    }

    private RuleNode parseImportName() {
        RuleNode r = rule("import_name");
        r.addToken(expect(PythonTokenType.NAME, "an imported name"));
        if (atKeyword("as")) { r.addToken(next()); r.addToken(expect(PythonTokenType.NAME, "an alias name")); }
        return r;
    }

    private RuleNode parseDottedName() {
        RuleNode r = rule("dotted_name");
        r.addToken(expect(PythonTokenType.NAME, "a module name"));
        while (atOp(".")) {
            r.addToken(next());
            r.addToken(expect(PythonTokenType.NAME, "a module name"));
        }
        return r;
    }

    // -------------------------------------------------------- compound statements

    private RuleNode parseDecoratedDefinition() {
        RuleNode r = rule("decorated_definition");
        while (atOp("@")) {
            RuleNode dec = rule("decorator");
            dec.addToken(next());
            dec.add(parseExpression());
            dec.addToken(expect(PythonTokenType.NEWLINE, "end of line after decorator"));
            r.add(dec);
        }
        if (!atKeyword("def")) throw syntaxError("Expected a function definition after decorator");
        r.add(parseFunctionDefinition());
        return r;
    }

    private RuleNode parseFunctionDefinition() {
        RuleNode r = rule("function_definition");
        r.addToken(expectKeyword("def"));
        r.addToken(expect(PythonTokenType.NAME, "a function name"));
        r.addToken(expectOp("("));
        if (!atOp(")")) r.add(parseParameterList());
        r.addToken(expectOp(")"));
        if (atOp("->")) throw syntaxError("Return type annotations are not supported");
        r.addToken(expectOp(":"));
        r.add(parseBlock());
        return r;
    }

    private RuleNode parseParameterList() {
        RuleNode r = rule("parameter_list");
        r.add(parseParameter());
        while (atOp(",")) {
            r.addToken(next());
            if (atOp(")")) break;
            r.add(parseParameter());
        }
        return r;
    }

    private RuleNode parseParameter() {
        RuleNode r = rule("parameter");
        if (atOp("*") || atOp("**")) throw syntaxError("*args / **kwargs parameters are not supported");
        r.addToken(expect(PythonTokenType.NAME, "a parameter name"));
        if (atOp(":")) throw syntaxError("Parameter type annotations are not supported");
        if (atOp("=")) { r.addToken(next()); r.add(parseExpression()); }
        return r;
    }

    private RuleNode parseIfStatement() {
        RuleNode r = rule("if_statement");
        r.addToken(expectKeyword("if"));
        r.add(parseExpression());
        r.addToken(expectOp(":"));
        r.add(parseBlock());
        while (atKeyword("elif")) {
            RuleNode elif = rule("elif_clause");
            elif.addToken(next());
            elif.add(parseExpression());
            elif.addToken(expectOp(":"));
            elif.add(parseBlock());
            r.add(elif);
        }
        if (atKeyword("else")) {
            RuleNode els = rule("else_clause");
            els.addToken(next());
            els.addToken(expectOp(":"));
            els.add(parseBlock());
            r.add(els);
        }
        return r;
    }

    private RuleNode parseForStatement() {
        RuleNode r = rule("for_statement");
        r.addToken(expectKeyword("for"));
        RuleNode targets = rule("target_list");
        targets.addToken(expect(PythonTokenType.NAME, "a loop variable name"));
        while (atOp(",")) {
            targets.addToken(next());
            targets.addToken(expect(PythonTokenType.NAME, "a loop variable name"));
        }
        r.add(targets);
        r.addToken(expectKeyword("in"));
        r.add(parseExpression());
        r.addToken(expectOp(":"));
        r.add(parseBlock());
        if (atKeyword("else")) throw syntaxError("'for ... else' is not supported");
        return r;
    }

    private RuleNode parseBlock() {
        RuleNode r = rule("block");
        if (!at(PythonTokenType.NEWLINE)) {
            // single-line suite: "if x: return y" -- keep the grammar small: not supported
            throw syntaxError("Expected an indented block on a new line but found " + describe(peek()));
        }
        r.addToken(next());
        if (!at(PythonTokenType.INDENT)) throw syntaxError("Expected an indented block");
        r.addToken(next());
        while (!at(PythonTokenType.DEDENT) && !at(PythonTokenType.EOF)) {
            if (at(PythonTokenType.NEWLINE)) { r.addToken(next()); continue; }
            r.add(parseStatement());
        }
        if (at(PythonTokenType.DEDENT)) r.addToken(next());
        return r;
    }

    // ------------------------------------------------------------- expressions

    public RuleNode parseExpression() {
        RuleNode r = rule("expression");
        r.add(parseOrTest());
        if (atKeyword("if")) throw syntaxError("Conditional expressions ('a if c else b') are not supported");
        return r;
    }

    private RuleNode parseOrTest() {
        RuleNode r = rule("or_test");
        r.add(parseAndTest());
        while (atKeyword("or")) { r.addToken(next()); r.add(parseAndTest()); }
        return r;
    }

    private RuleNode parseAndTest() {
        RuleNode r = rule("and_test");
        r.add(parseNotTest());
        while (atKeyword("and")) { r.addToken(next()); r.add(parseNotTest()); }
        return r;
    }

    private RuleNode parseNotTest() {
        RuleNode r = rule("not_test");
        if (atKeyword("not")) { r.addToken(next()); r.add(parseNotTest()); return r; }
        r.add(parseComparison());
        return r;
    }

    private RuleNode parseComparison() {
        RuleNode r = rule("comparison");
        r.add(parseArithExpr());
        while (true) {
            RuleNode op = rule("comparison_operator");
            if (atOp("==") || atOp("!=") || atOp("<") || atOp(">") || atOp("<=") || atOp(">=")) {
                op.addToken(next());
            } else if (atKeyword("in")) {
                op.addToken(next());
            } else if (atKeyword("not") && peek(1).is(PythonTokenType.KEYWORD, "in")) {
                op.addToken(next()); op.addToken(next());
            } else if (atKeyword("is")) {
                op.addToken(next());
                if (atKeyword("not")) op.addToken(next());
            } else {
                break;
            }
            r.add(op);
            r.add(parseArithExpr());
        }
        return r;
    }

    // الجمع والطرح في مستوى أعلى من الحدود؛ لذلك ينادي هذا المستوى قاعدة الضرب أولا.
    private RuleNode parseArithExpr() {
        RuleNode r = rule("arith_expr");
        r.add(parseTerm());
        while (atOp("+") || atOp("-")) { r.addToken(next()); r.add(parseTerm()); }
        return r;
    }

    // هذا المستوى يجمع الضرب والقسمة قبل الجمع؛ مثال: ٢ + ٣ × ٤ يُفسر مع الضرب أولا.
    private RuleNode parseTerm() {
        RuleNode r = rule("term");
        r.add(parseFactor());
        while (atOp("*") || atOp("/") || atOp("//") || atOp("%")) { r.addToken(next()); r.add(parseFactor()); }
        return r;
    }

    // يعالج الإشارة الأحادية والأجزاء الأولية، ثم تعود النتيجة إلى قواعد الأولوية الأعلى.
    private RuleNode parseFactor() {
        RuleNode r = rule("factor");
        if (atOp("-") || atOp("+")) { r.addToken(next()); r.add(parseFactor()); return r; }
        r.add(parsePrimary());
        return r;
    }

    private RuleNode parsePrimary() {
        RuleNode r = rule("primary");
        r.add(parseAtom());
        while (atOp(".") || atOp("(") || atOp("[")) {
            RuleNode trailer = rule("trailer");
            if (atOp(".")) {
                trailer.addToken(next());
                trailer.addToken(expect(PythonTokenType.NAME, "an attribute name after '.'"));
            } else if (atOp("(")) {
                trailer.addToken(next());
                if (!atOp(")")) trailer.add(parseArgumentList());
                trailer.addToken(expectOp(")"));
            } else {
                trailer.addToken(next());
                if (atOp(":")) throw syntaxError("Slicing is not supported");
                trailer.add(parseExpression());
                if (atOp(":")) throw syntaxError("Slicing is not supported");
                trailer.addToken(expectOp("]"));
            }
            r.add(trailer);
        }
        return r;
    }

    private RuleNode parseArgumentList() {
        RuleNode r = rule("argument_list");
        r.add(parseArgument());
        while (atOp(",")) {
            r.addToken(next());
            if (atOp(")")) break;
            r.add(parseArgument());
        }
        return r;
    }

    private RuleNode parseArgument() {
        RuleNode r = rule("argument");
        if (atOp("*") || atOp("**")) throw syntaxError("Argument unpacking (*args / **kwargs) is not supported");
        if (at(PythonTokenType.NAME) && peek(1).is(PythonTokenType.OP, "=")) {
            r.addToken(next());
            r.addToken(next());
        }
        r.add(parseExpression());
        return r;
    }

    private RuleNode parseAtom() {
        RuleNode r = rule("atom");
        Token t = peek();
        if (t.is(PythonTokenType.NAME)) { r.addToken(next()); return r; }
        if (t.is(PythonTokenType.NUMBER)) { r.addToken(next()); return r; }
        if (t.is(PythonTokenType.STRING)) {
            while (at(PythonTokenType.STRING)) r.addToken(next());
            return r;
        }
        if (t.is(PythonTokenType.KEYWORD) && (t.text().equals("True") || t.text().equals("False") || t.text().equals("None"))) {
            r.addToken(next());
            return r;
        }
        if (atOp("[")) { r.add(parseListDisplay()); return r; }
        if (atOp("{")) { r.add(parseDictDisplay()); return r; }
        if (atOp("(")) {
            r.addToken(next());
            if (atOp(")")) throw syntaxError("Empty tuples are not supported");
            r.add(parseExpression());
            if (atOp(",")) throw syntaxError("Tuples are not supported");
            r.addToken(expectOp(")"));
            return r;
        }
        if (t.is(PythonTokenType.KEYWORD) && UNSUPPORTED_KEYWORDS.contains(t.text())) {
            throw new CompilerException(ErrorCategory.SYNTAX, "SYN002", "UnsupportedConstruct",
                    "'" + t.text() + "' is not part of the supported Python subset", t.position());
        }
        throw syntaxError("Unexpected " + describe(t) + " in expression");
    }

    private RuleNode parseListDisplay() {
        RuleNode r = rule("list_display");
        r.addToken(expectOp("["));
        while (at(PythonTokenType.NEWLINE)) next();
        if (!atOp("]")) {
            r.add(parseExpression());
            if (atKeyword("for")) throw syntaxError("List comprehensions are not supported");
            while (atOp(",")) {
                r.addToken(next());
                if (atOp("]")) break;
                r.add(parseExpression());
            }
        }
        r.addToken(expectOp("]"));
        return r;
    }

    private RuleNode parseDictDisplay() {
        RuleNode r = rule("dict_display");
        r.addToken(expectOp("{"));
        if (!atOp("}")) {
            r.add(parseDictEntry());
            while (atOp(",")) {
                r.addToken(next());
                if (atOp("}")) break;
                r.add(parseDictEntry());
            }
        }
        r.addToken(expectOp("}"));
        return r;
    }

    private RuleNode parseDictEntry() {
        RuleNode r = rule("dict_entry");
        r.add(parseExpression());
        if (atOp(",") || atOp("}")) throw syntaxError("Set literals are not supported; expected ':' after dictionary key");
        r.addToken(expectOp(":"));
        r.add(parseExpression());
        return r;
    }
}
