package fjc.jinja;

import fjc.common.CompilerException;
import fjc.common.Token;
import fjc.jinja.lexer.JinjaLexer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JinjaLexerTest {

    private static List<String> types(String src) {
        List<String> out = new ArrayList<>();
        for (Token t : new JinjaLexer("t.jinja", src).tokenize()) out.add(t.type().name() + (t.text().isEmpty() ? "" : ":" + t.text()));
        return out;
    }

    @Test
    void tokenizesHtmlWithOutputExpressions() {
        assertEquals(List.of("TAG_OPEN:p", "ATTR_NAME:class", "ATTR_EQ:=", "ATTR_VALUE_START:\"", "ATTR_TEXT:x", "ATTR_VALUE_END:\"",
                "TAG_END:>", "VAR_BEGIN:{{", "NAME:product", "OP:.", "NAME:name", "VAR_END:}}", "TAG_CLOSE:p", "EOF"),
                types("<p class=\"x\">{{ product.name }}</p>"));
    }

    @Test
    void tokenizesBlocksKeywordsFiltersAndStrings() {
        assertEquals(List.of("BLOCK_BEGIN:{%", "KEYWORD:for", "NAME:p", "KEYWORD:in", "NAME:items", "OP:|", "NAME:sort", "BLOCK_END:%}",
                "TEXT:x", "BLOCK_BEGIN:{%", "KEYWORD:endfor", "BLOCK_END:%}", "EOF"),
                types("{% for p in items|sort %}x{% endfor %}"));
        assertEquals(List.of("VAR_BEGIN:{{", "STRING:%.2f", "OP:|", "NAME:format", "OP:(", "NUMBER:3.5", "OP:)", "VAR_END:}}", "EOF"),
                types("{{ \"%.2f\"|format(3.5) }}"));
    }

    @Test
    void handlesJinjaInsideAttributesAndTags() {
        List<String> t = types("<option {% if a %}selected{% endif %} value=\"{{ v }}\">");
        assertEquals("BLOCK_BEGIN:{%", t.get(1));
        assertEquals("ATTR_NAME:selected", t.get(5));
        assertEquals("VAR_BEGIN:{{", t.get(12));
    }

    @Test
    void scriptContentIsRawText() {
        assertEquals(List.of("TAG_OPEN:script", "TAG_END:>", "TEXT:if (a < b) { x(); }", "TAG_CLOSE:script", "EOF"),
                types("<script>if (a < b) { x(); }</script>"));
    }

    @Test
    void doctypeCommentsAndEntitiesAreRecognised() {
        assertEquals(List.of("DOCTYPE:DOCTYPE html", "TEXT:\n", "HTML_COMMENT: c ", "TEXT:&amp; 1 < 2", "EOF"),
                types("<!DOCTYPE html>\n<!-- c -->&amp; 1 < 2"));
    }

    @Test
    void jinjaCommentsAreSkipped() {
        assertEquals(List.of("TEXT:a", "TEXT:b", "EOF"), types("a{# hidden #}b"));
    }

    @Test
    void reportsUnterminatedDelimiterWithPosition() {
        CompilerException e = assertThrows(CompilerException.class, () -> types("<p>\n{{ product.name </p>"));
        assertEquals("LEX101", e.error().code());
    }

    @Test
    void reportsIllegalCharacterInExpression() {
        CompilerException e = assertThrows(CompilerException.class, () -> types("{{ a $ b }}"));
        assertEquals("LEX110", e.error().code());
        assertEquals(1, e.error().line());
        assertEquals(6, e.error().column());
    }

    @Test
    void reportsUnterminatedAttributeValue() {
        CompilerException e = assertThrows(CompilerException.class, () -> types("<a href=\"x>text</a>"));
        assertEquals("LEX109", e.error().code());
    }

    @Test
    void tracksLinesAcrossText() {
        List<Token> tokens = new JinjaLexer("t.jinja", "<div>\n  <p>\n{{ x }}</p></div>").tokenize();
        Token var = tokens.stream().filter(t -> t.text().equals("{{")).findFirst().orElseThrow();
        assertEquals(3, var.line());
        assertEquals(1, var.column());
    }
}
