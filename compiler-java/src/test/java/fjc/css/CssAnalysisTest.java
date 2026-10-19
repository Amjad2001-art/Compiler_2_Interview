package fjc.css;

import fjc.common.CompilerException;
import fjc.common.ErrorCollector;
import fjc.parsetree.RuleNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssAnalysisTest {

    static Stylesheet sheet(String css) {
        RuleNode tree = new CssParser(new CssLexer("style.css", css).tokenize()).parseStylesheet();
        return new CssAstBuilder("style.css").build(tree);
    }

    static ErrorCollector analyze(String css) {
        ErrorCollector errors = new ErrorCollector();
        new CssAnalyzer(errors).analyze(sheet(css));
        return errors;
    }

    @Test
    void parsesRulesDeclarationsAndAtRules() {
        Stylesheet s = sheet(":root { --c: #fff; }\n.btn, a:hover { color: var(--c); margin: 0 auto !important; }\n@media (max-width: 640px) { .btn { display: none; } }");
        assertEquals(3, s.rules().size());
        CssRule btn = (CssRule) s.rules().get(1);
        assertEquals(".btn, a:hover", btn.selectorText());
        assertEquals(2, btn.declarations().size());
        assertTrue(btn.declarations().get(1).isImportant());
        assertEquals("var(--c)", btn.declarations().get(0).value());
        CssAtRule media = (CssAtRule) s.rules().get(2);
        assertEquals("media", media.name());
        assertEquals(1, media.rules().size());
        assertEquals(3, ((CssRule) media.rules().get(0)).line());
    }

    @Test
    void detectsUndefinedCustomProperty() {
        ErrorCollector e = analyze(":root { --a: 1px; }\n.x { width: var(--b); }");
        assertTrue(e.hasCode("CSS002"));
        assertEquals(2, e.withCode("CSS002").get(0).line());
    }

    @Test
    void detectsUnknownPropertyInvalidColorAndDuplicates() {
        ErrorCollector e = analyze(".x { colour: red; color: #ggg; color: #fff; }");
        assertTrue(e.hasCode("CSS001"));
        assertTrue(e.hasCode("CSS005"));
        assertTrue(e.hasCode("CSS003"));
    }

    @Test
    void validStylesheetHasNoErrors() {
        ErrorCollector e = analyze(".x { color: #fff; margin: 1rem 2px; background: url(a.png) no-repeat; -webkit-line-clamp: 2; }");
        assertFalse(e.hasErrors(), e.all().toString());
    }

    @Test
    void reportsSyntaxErrorsWithPosition() {
        CompilerException ex = assertThrows(CompilerException.class, () -> sheet(".x { color red; }"));
        assertEquals("SYN201", ex.error().code());
        assertEquals(1, ex.error().line());
    }
}
