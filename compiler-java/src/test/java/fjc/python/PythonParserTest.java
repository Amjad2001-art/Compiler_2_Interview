package fjc.python;

import fjc.common.CompilerException;
import fjc.parsetree.RuleNode;
import fjc.python.lexer.PythonLexer;
import fjc.python.parser.PythonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonParserTest {

    static RuleNode parse(String src) {
        return new PythonParser(new PythonLexer("t.py", src).tokenize()).parseModule();
    }

    @Test
    void parseTreeKeepsEveryGrammarProductionAndToken() {
        RuleNode module = parse("x = (1 + 2) * 3\n");
        RuleNode statement = module.rule("statement");
        RuleNode assignment = statement.rule("simple_statement").rule("assignment_statement");
        assertNotNull(assignment);
        assertTrue(assignment.hasToken("="));
        // the value goes through the whole precedence chain, including the parentheses
        RuleNode value = assignment.rules("expression").get(1);
        RuleNode term = value.rule("or_test").rule("and_test").rule("not_test").rule("comparison").rule("arith_expr").rule("term");
        assertEquals(2, term.rules("factor").size());
        assertTrue(term.hasToken("*"));
        RuleNode atom = term.rules("factor").get(0).rule("primary").rule("atom");
        assertTrue(atom.hasToken("(") && atom.hasToken(")"), "parentheses are kept in the parse tree");
        assertTrue(module.tokenTexts().contains("("));
    }

    @Test
    void parsesRouteDecoratorFunctionIfForAndCalls() {
        RuleNode module = parse("@app.route('/x', methods=['GET'])\ndef f(a, b=2):\n    for p in items:\n        if p['id'] == a:\n            return render_template('t.jinja', p=p)\n    return None\n");
        RuleNode decorated = module.rule("statement").rule("decorated_definition");
        assertNotNull(decorated);
        assertEquals(1, decorated.rules("decorator").size());
        RuleNode fn = decorated.rule("function_definition");
        assertEquals(2, fn.rule("parameter_list").rules("parameter").size());
        RuleNode block = fn.rule("block");
        assertEquals(2, block.rules("statement").size());
        RuleNode forStmt = block.rules("statement").get(0).rule("for_statement");
        assertNotNull(forStmt);
        assertNotNull(forStmt.rule("block").rule("statement").rule("if_statement"));
    }

    @Test
    void reportsMissingColonWithPosition() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("def f()\n    return 1\n"));
        assertEquals("SYN001", e.error().code());
        assertEquals(1, e.error().line());
        assertEquals(8, e.error().column());
    }

    @Test
    void reportsUnsupportedConstructsInsteadOfCrashing() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("while True:\n    pass\n"));
        assertEquals("SYN002", e.error().code());
        assertTrue(e.error().message().contains("while"));
        CompilerException c = assertThrows(CompilerException.class, () -> parse("xs = [p for p in items]\n"));
        assertTrue(c.error().message().contains("comprehension"));
        CompilerException l = assertThrows(CompilerException.class, () -> parse("f = lambda x: x\n"));
        assertEquals("SYN002", l.error().code());
    }

    @Test
    void reportsUnexpectedIndentation() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("x = 1\n    y = 2\n"));
        assertEquals(2, e.error().line());
    }

    @Test
    void parsesImportsAndComparisonChains() {
        RuleNode module = parse("from flask import Flask, render_template as rt\nimport os\nok = a < b <= c and not d\n");
        assertEquals(3, module.rules("statement").size());
        RuleNode fromImport = module.rules("statement").get(0).rule("simple_statement").rule("from_import_statement");
        assertEquals(2, fromImport.rules("import_name").size());
        assertTrue(fromImport.rules("import_name").get(1).hasToken("as"));
    }
}
