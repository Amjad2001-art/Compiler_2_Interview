package fjc.jinja;

import fjc.common.CompilerException;
import fjc.jinja.lexer.JinjaLexer;
import fjc.jinja.parser.JinjaParser;
import fjc.parsetree.RuleNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JinjaParserTest {

    static RuleNode parse(String src) {
        return new JinjaParser(new JinjaLexer("t.jinja", src).tokenize()).parseTemplate();
    }

    @Test
    void parseTreeKeepsDelimitersTagsAndProductions() {
        RuleNode template = parse("<ul>{% for p in items %}<li>{{ p.name|upper }}</li>{% endfor %}</ul>");
        RuleNode element = template.rule("node").rule("element");
        assertNotNull(element.rule("start_tag"));
        assertNotNull(element.rule("end_tag"));
        RuleNode forBlock = element.rule("node").rule("for_block");
        assertNotNull(forBlock.rule("for_tag"));
        assertNotNull(forBlock.rule("endfor_tag"));
        assertTrue(forBlock.rule("for_tag").hasToken("{%") && forBlock.rule("for_tag").hasToken("%}"));
        RuleNode output = forBlock.rule("node").rule("element").rule("node").rule("output");
        assertTrue(output.hasToken("{{") && output.hasToken("}}"));
        RuleNode postfix = output.rule("expression").rule("or_test").rule("and_test").rule("not_test").rule("comparison")
                .rule("additive").rule("multiplicative").rule("unary").rule("postfix");
        assertEquals(2, postfix.rules("trailer").size(), "'.name' and '|upper' trailers");
    }

    @Test
    void reportsMismatchedClosingTag() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("<div>\n<span>x</div>"));
        assertEquals("SYN103", e.error().code());
        assertEquals(2, e.error().line());
        assertTrue(e.error().message().contains("</span>"));
    }

    @Test
    void reportsUnclosedForBlock() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("{% for p in items %}<p>{{ p }}</p>"));
        assertEquals("SYN105", e.error().code());
    }

    @Test
    void reportsElementNotClosedInsideBlock() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("{% if a %}<div>{% endif %}</div>"));
        assertEquals("SYN104", e.error().code());
    }

    @Test
    void reportsUnexpectedEndTag() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("<p>x</p>{% endfor %}"));
        assertEquals("SYN106", e.error().code());
    }

    @Test
    void reportsUnsupportedTag() {
        CompilerException e = assertThrows(CompilerException.class, () -> parse("{% extends 'base.jinja' %}"));
        assertEquals("SYN102", e.error().code());
    }

    @Test
    void voidElementsNeedNoClosingTag() {
        RuleNode template = parse("<img src=\"a.png\" alt=\"a\"><br><input type=\"text\">");
        assertEquals(3, template.rules("node").size());
    }

    @Test
    void parsesIfElifElseInsideAttributesAndValues() {
        RuleNode template = parse("<option {% if a %}selected{% elif b %}disabled{% else %}data-x=\"1\"{% endif %} class=\"c{% if d %} e{% endif %}\">x</option>");
        RuleNode startTag = template.rule("node").rule("element").rule("start_tag");
        RuleNode attrIf = startTag.rules("attribute_item").get(0).rule("attr_if_block");
        assertNotNull(attrIf);
        assertEquals(1, attrIf.rules("elif_clause").size());
        assertNotNull(attrIf.rule("else_clause"));
        RuleNode classAttr = startTag.rules("attribute_item").get(1).rule("attribute");
        assertNotNull(classAttr.rule("attr_value").rules("attr_part").get(1).rule("value_if_block"));
    }
}
