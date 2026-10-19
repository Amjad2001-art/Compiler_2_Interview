package fjc.python;

import fjc.common.CompilerException;
import fjc.common.Token;
import fjc.python.lexer.PythonLexer;
import fjc.python.lexer.PythonTokenType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonLexerTest {

    private static List<String> types(String src) {
        List<String> out = new ArrayList<>();
        for (Token t : new PythonLexer("t.py", src).tokenize()) out.add(t.type().name() + (t.text().isEmpty() ? "" : ":" + t.text()));
        return out;
    }

    @Test
    void tokenizesAssignmentWithListAndDict() {
        List<String> t = types("products = [{\"id\": 1, \"price\": 2.5}]\n");
        assertEquals(List.of("NAME:products", "OP:=", "OP:[", "OP:{", "STRING:id", "OP::", "NUMBER:1", "OP:,",
                "STRING:price", "OP::", "NUMBER:2.5", "OP:}", "OP:]", "NEWLINE:\n", "EOF"), t);
    }

    @Test
    void producesIndentAndDedentForBlocks() {
        List<String> t = types("def f():\n    return 1\nx = 2\n");
        assertEquals(List.of("KEYWORD:def", "NAME:f", "OP:(", "OP:)", "OP::", "NEWLINE:\n", "INDENT",
                "KEYWORD:return", "NUMBER:1", "NEWLINE:\n", "DEDENT", "NAME:x", "OP:=", "NUMBER:2", "NEWLINE:\n", "EOF"), t);
    }

    @Test
    void skipsBlankLinesAndCommentsWithoutTokens() {
        List<String> t = types("a = 1\n\n# comment\n   \nb = 2\n");
        assertEquals(List.of("NAME:a", "OP:=", "NUMBER:1", "NEWLINE:\n", "NAME:b", "OP:=", "NUMBER:2", "NEWLINE:\n", "EOF"), t);
    }

    @Test
    void joinsLinesInsideBracketsAndEmitsNewlineAfterClosingBracket() {
        List<String> t = types("xs = [\n    1,\n    2\n]\ny = 3\n");
        assertTrue(t.contains("NEWLINE:\n"));
        int close = t.indexOf("OP:]");
        assertEquals("NEWLINE:\n", t.get(close + 1), "a NEWLINE must follow the closing bracket");
        assertEquals(1, t.stream().filter(s -> s.equals("NEWLINE:\n")).count() - 1, "no NEWLINE tokens inside the brackets");
    }

    @Test
    void decodesStringEscapesAndTripleQuotes() {
        List<Token> tokens = new PythonLexer("t.py", "s = 'a\\'b'\nd = \"\"\"multi\nline\"\"\"\n").tokenize();
        assertEquals("a'b", tokens.get(2).text());
        assertEquals("multi\nline", tokens.get(6).text());
        assertEquals(2, tokens.get(6).line());
    }

    @Test
    void recordsLineAndColumnOfEveryToken() {
        List<Token> tokens = new PythonLexer("t.py", "x = 1\n  \nname = \"a\"\n").tokenize();
        Token name = tokens.get(4);
        assertEquals("name", name.text());
        assertEquals(3, name.line());
        assertEquals(1, name.column());
        assertEquals(8, tokens.get(6).column());
    }

    @Test
    void reportsIllegalCharacterWithPosition() {
        CompilerException e = assertThrows(CompilerException.class, () -> new PythonLexer("t.py", "x = 1\ny = $\n").tokenize());
        assertEquals("LEX001", e.error().code());
        assertEquals(2, e.error().line());
        assertEquals(5, e.error().column());
    }

    @Test
    void reportsUnterminatedString() {
        CompilerException e = assertThrows(CompilerException.class, () -> new PythonLexer("t.py", "x = \"abc\n").tokenize());
        assertEquals("LEX002", e.error().code());
    }

    @Test
    void rejectsFStringsAsUnsupported() {
        CompilerException e = assertThrows(CompilerException.class, () -> new PythonLexer("t.py", "x = f\"{y}\"\n").tokenize());
        assertEquals("LEX004", e.error().code());
    }

    @Test
    void reportsInconsistentDedent() {
        CompilerException e = assertThrows(CompilerException.class,
                () -> new PythonLexer("t.py", "def f():\n        return 1\n    x = 2\n").tokenize());
        assertEquals("LEX003", e.error().code());
        assertEquals(3, e.error().line());
    }

    @Test
    void keywordsAreDistinguishedFromNames() {
        List<Token> tokens = new PythonLexer("t.py", "for x in xs:\n    pass\n").tokenize();
        assertEquals(PythonTokenType.KEYWORD, tokens.get(0).type());
        assertEquals(PythonTokenType.NAME, tokens.get(1).type());
        assertEquals(PythonTokenType.KEYWORD, tokens.get(2).type());
    }
}
