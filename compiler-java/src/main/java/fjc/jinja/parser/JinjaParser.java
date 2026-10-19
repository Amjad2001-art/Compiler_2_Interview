package fjc.jinja.parser;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourcePosition;
import fjc.common.Token;
import fjc.jinja.lexer.JinjaTokenType;
import fjc.parsetree.RuleNode;

import java.util.List;
import java.util.Set;

/**
 * Recursive-descent parser for Jinja templates with HTML structure. Produces a
 * concrete parse tree; {@code JinjaAstBuilder} compresses it into the Jinja AST.
 *
 * <pre>
 * template        := node* EOF
 * node            := text | doctype | comment | element | output | for_block | if_block
 * element         := start_tag node* end_tag            (non-void elements)
 *                  | start_tag                          (void elements, or self-closed)
 * start_tag       := TAG_OPEN attribute_item* (TAG_END | TAG_SELF_CLOSE)
 * attribute_item  := attribute | output | attr_if_block
 * attribute       := ATTR_NAME (ATTR_EQ attr_value)?
 * attr_value      := ATTR_VALUE_START attr_part* ATTR_VALUE_END | ATTR_UNQUOTED
 * attr_part       := ATTR_TEXT | output | value_if_block
 * end_tag         := TAG_CLOSE
 * output          := VAR_BEGIN expression VAR_END
 * for_block       := for_tag node* endfor_tag
 * for_tag         := BLOCK_BEGIN 'for' loop_targets 'in' expression BLOCK_END
 * loop_targets    := NAME (',' NAME)*
 * endfor_tag      := BLOCK_BEGIN 'endfor' BLOCK_END
 * if_block        := if_tag node* elif_clause* else_clause? endif_tag
 * elif_clause     := BLOCK_BEGIN 'elif' expression BLOCK_END node*
 * else_clause     := BLOCK_BEGIN 'else' BLOCK_END node*
 * endif_tag       := BLOCK_BEGIN 'endif' BLOCK_END
 * attr_if_block / value_if_block := same shape as if_block with attribute_item* / attr_part* bodies
 * expression      := or_test
 * or_test         := and_test ('or' and_test)*
 * and_test        := not_test ('and' not_test)*
 * not_test        := 'not' not_test | comparison
 * comparison      := additive (comparison_operator additive | test)*
 * comparison_operator := '==' | '!=' | '<' | '>' | '<=' | '>=' | 'in' | 'not' 'in'
 * test            := 'is' 'not'? NAME ('(' call_args? ')' | atom)?
 * additive        := multiplicative (('+' | '-' | '~') multiplicative)*
 * multiplicative  := unary (('*' | '/' | '//' | '%') unary)*
 * unary           := ('-' | '+') unary | postfix
 * postfix         := atom trailer*
 * trailer         := '.' NAME | '[' expression ']' | '(' call_args? ')' | filter
 * filter          := '|' NAME ('(' call_args? ')')?
 * call_args       := call_arg (',' call_arg)* ','?
 * call_arg        := NAME '=' expression | expression
 * atom            := NAME | STRING | NUMBER | 'true' | 'false' | 'none' | list_literal | '(' expression ')'
 * list_literal    := '[' (expression (',' expression)*)? ']'
 * </pre>
 */
public final class JinjaParser {

    public static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");

    private static final Set<String> BLOCK_TERMINATORS = Set.of("endfor", "elif", "else", "endif");

    private final List<Token> tokens;
    private int index = 0;

    public JinjaParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // ---------------------------------------------------------------- helpers

    private Token peek() { return tokens.get(index); }
    private Token peek(int ahead) { return tokens.get(Math.min(index + ahead, tokens.size() - 1)); }
    private boolean at(JinjaTokenType t) { return peek().is(t); }
    private boolean atOp(String s) { return peek().is(JinjaTokenType.OP, s); }
    private boolean atKeyword(String s) { return peek().is(JinjaTokenType.KEYWORD, s); }
    private boolean atBlock(String keyword) { return at(JinjaTokenType.BLOCK_BEGIN) && peek(1).is(JinjaTokenType.KEYWORD, keyword); }
    private boolean atBlockTerminator() {
        return at(JinjaTokenType.BLOCK_BEGIN) && peek(1).is(JinjaTokenType.KEYWORD) && BLOCK_TERMINATORS.contains(peek(1).text());
    }

    private Token next() {
        Token t = peek();
        if (!t.is(JinjaTokenType.EOF)) index++;
        return t;
    }

    private RuleNode rule(String name) { return new RuleNode(name, peek().position()); }

    private CompilerException error(String code, String name, String message, SourcePosition at) {
        return new CompilerException(ErrorCategory.SYNTAX, code, name, message, at);
    }

    private CompilerException syntax(String message) {
        return error("SYN101", "JinjaSyntaxError", message, peek().position());
    }

    private String describe(Token t) {
        switch ((JinjaTokenType) t.type()) {
            case EOF: return "end of file";
            case TEXT: return "text";
            case TAG_OPEN: return "'<" + t.text() + "'";
            case TAG_CLOSE: return "'</" + t.text() + ">'";
            case STRING: return "string literal";
            default: return "'" + t.text() + "'";
        }
    }

    private Token expect(JinjaTokenType type, String what) {
        if (!at(type)) throw syntax("Expected " + what + " but found " + describe(peek()));
        return next();
    }

    private Token expectOp(String op) {
        if (!atOp(op)) throw syntax("Expected '" + op + "' but found " + describe(peek()));
        return next();
    }

    private Token expectKeyword(String kw) {
        if (!atKeyword(kw)) throw syntax("Expected '" + kw + "' but found " + describe(peek()));
        return next();
    }

    // --------------------------------------------------------------- template

    // جذر قالب جينجا يجمع النصوص والوسوم والتعابير ويكشف النهايات غير المتطابقة.
    public RuleNode parseTemplate() {
        RuleNode template = rule("template");
        parseNodesInto(template, "template");
        if (at(JinjaTokenType.TAG_CLOSE)) {
            throw error("SYN106", "UnexpectedClosingTag",
                    "Closing tag </" + peek().text() + "> has no matching opening tag", peek().position());
        }
        if (atBlockTerminator()) {
            throw error("SYN106", "UnexpectedBlockEnd",
                    "'{% " + peek(1).text() + " %}' has no matching opening block", peek().position());
        }
        template.addToken(expect(JinjaTokenType.EOF, "end of file"));
        return template;
    }

    /** Parses node* until a stop token (TAG_CLOSE, block terminator or EOF). */
    private void parseNodesInto(RuleNode parent, String context) {
        while (!at(JinjaTokenType.EOF) && !at(JinjaTokenType.TAG_CLOSE) && !atBlockTerminator()) {
            parent.add(parseNode());
        }
    }

    // ترتيب الفروع يحدد هل العنصر نص أو وسم أو تعبير إخراج أو كتلة تحكم.
    private RuleNode parseNode() {
        RuleNode node = rule("node");
        Token t = peek();
        switch ((JinjaTokenType) t.type()) {
            case TEXT: {
                RuleNode text = rule("text");
                text.addToken(next());
                node.add(text);
                return node;
            }
            case DOCTYPE: {
                RuleNode d = rule("doctype");
                d.addToken(next());
                node.add(d);
                return node;
            }
            case HTML_COMMENT: {
                RuleNode c = rule("comment");
                c.addToken(next());
                node.add(c);
                return node;
            }
            case TAG_OPEN: node.add(parseElement()); return node;
            case VAR_BEGIN: node.add(parseOutput()); return node;
            case BLOCK_BEGIN: node.add(parseBlock(BodyKind.NODES)); return node;
            default:
                throw syntax("Unexpected " + describe(t));
        }
    }

    // ---------------------------------------------------------------- element

    private RuleNode parseElement() {
        RuleNode element = rule("element");
        Token open = peek();
        String tagName = open.text();
        RuleNode startTag = rule("start_tag");
        startTag.addToken(next());
        while (!at(JinjaTokenType.TAG_END) && !at(JinjaTokenType.TAG_SELF_CLOSE)) {
            if (at(JinjaTokenType.EOF)) throw syntax("Unterminated start tag <" + tagName + ">");
            startTag.add(parseAttributeItem());
        }
        boolean selfClosed = at(JinjaTokenType.TAG_SELF_CLOSE);
        startTag.addToken(next());
        element.add(startTag);
        if (selfClosed || VOID_ELEMENTS.contains(tagName)) return element;

        parseNodesInto(element, "<" + tagName + ">");
        if (at(JinjaTokenType.TAG_CLOSE)) {
            Token close = peek();
            if (!close.text().equals(tagName)) {
                throw error("SYN103", "MismatchedClosingTag",
                        "Mismatched closing tag </" + close.text() + ">: expected </" + tagName + "> (opened at line "
                                + open.line() + ", column " + open.column() + ")", close.position());
            }
            RuleNode endTag = rule("end_tag");
            endTag.addToken(next());
            element.add(endTag);
            return element;
        }
        if (atBlockTerminator()) {
            throw error("SYN104", "UnclosedElement",
                    "Element <" + tagName + "> opened at line " + open.line() + " is not closed before '{% "
                            + peek(1).text() + " %}' (HTML elements must be closed inside the same Jinja block)",
                    peek().position());
        }
        throw error("SYN104", "UnclosedElement",
                "Element <" + tagName + "> opened at line " + open.line() + ", column " + open.column()
                        + " is never closed", open.position());
    }

    private RuleNode parseAttributeItem() {
        RuleNode item = rule("attribute_item");
        if (at(JinjaTokenType.ATTR_NAME)) {
            RuleNode attribute = rule("attribute");
            attribute.addToken(next());
            if (at(JinjaTokenType.ATTR_EQ)) {
                attribute.addToken(next());
                attribute.add(parseAttrValue());
            }
            item.add(attribute);
            return item;
        }
        if (at(JinjaTokenType.VAR_BEGIN)) { item.add(parseOutput()); return item; }
        if (at(JinjaTokenType.BLOCK_BEGIN)) { item.add(parseBlock(BodyKind.ATTRIBUTE_ITEMS)); return item; }
        throw syntax("Unexpected " + describe(peek()) + " inside start tag");
    }

    private RuleNode parseAttrValue() {
        RuleNode value = rule("attr_value");
        if (at(JinjaTokenType.ATTR_UNQUOTED)) { value.addToken(next()); return value; }
        if (at(JinjaTokenType.VAR_BEGIN)) {          // href={{ x }} (unquoted expression)
            value.add(parseOutput());
            return value;
        }
        value.addToken(expect(JinjaTokenType.ATTR_VALUE_START, "an attribute value"));
        while (!at(JinjaTokenType.ATTR_VALUE_END)) {
            if (at(JinjaTokenType.EOF)) throw syntax("Unterminated attribute value");
            value.add(parseAttrPart());
        }
        value.addToken(next());
        return value;
    }

    private RuleNode parseAttrPart() {
        RuleNode part = rule("attr_part");
        if (at(JinjaTokenType.ATTR_TEXT)) { part.addToken(next()); return part; }
        if (at(JinjaTokenType.VAR_BEGIN)) { part.add(parseOutput()); return part; }
        if (at(JinjaTokenType.BLOCK_BEGIN)) { part.add(parseBlock(BodyKind.ATTR_PARTS)); return part; }
        throw syntax("Unexpected " + describe(peek()) + " inside attribute value");
    }

    // ----------------------------------------------------------------- blocks

    private enum BodyKind { NODES, ATTRIBUTE_ITEMS, ATTR_PARTS }

    private void parseBodyInto(RuleNode parent, BodyKind kind) {
        switch (kind) {
            case NODES: parseNodesInto(parent, "block"); break;
            case ATTRIBUTE_ITEMS:
                while (!atBlockTerminator() && !at(JinjaTokenType.TAG_END) && !at(JinjaTokenType.TAG_SELF_CLOSE)) {
                    if (at(JinjaTokenType.EOF)) throw syntax("Unterminated start tag");
                    parent.add(parseAttributeItem());
                }
                break;
            case ATTR_PARTS:
                while (!atBlockTerminator() && !at(JinjaTokenType.ATTR_VALUE_END)) {
                    if (at(JinjaTokenType.EOF)) throw syntax("Unterminated attribute value");
                    parent.add(parseAttrPart());
                }
                break;
            default: throw new IllegalStateException();
        }
    }

    private RuleNode parseOutput() {
        RuleNode output = rule("output");
        output.addToken(expect(JinjaTokenType.VAR_BEGIN, "'{{'"));
        output.add(parseExpression());
        output.addToken(expect(JinjaTokenType.VAR_END, "'}}'"));
        return output;
    }

    private RuleNode parseBlock(BodyKind kind) {
        Token begin = peek();
        Token keyword = peek(1);
        if (!keyword.is(JinjaTokenType.KEYWORD) && !keyword.is(JinjaTokenType.NAME)) {
            throw error("SYN101", "JinjaSyntaxError", "Expected a block keyword after '{%'", keyword.position());
        }
        switch (keyword.text()) {
            case "for": return parseForBlock(kind);
            case "if": return parseIfBlock(kind);
            case "endfor": case "endif": case "elif": case "else":
                throw error("SYN106", "UnexpectedBlockEnd",
                        "'{% " + keyword.text() + " %}' has no matching opening block", begin.position());
            default:
                throw error("SYN102", "UnsupportedJinjaTag",
                        "Jinja tag '{% " + keyword.text() + " %}' is not supported (supported: for, if/elif/else)",
                        keyword.position());
        }
    }

    // يحلل حلقة التكرار من متغيراتها والمصدر الذي تتكرر عليه حتى وسم الإغلاق المقابل.
    private RuleNode parseForBlock(BodyKind kind) {
        String ruleName = kind == BodyKind.NODES ? "for_block" : kind == BodyKind.ATTRIBUTE_ITEMS ? "attr_for_block" : "value_for_block";
        RuleNode block = rule(ruleName);
        Token begin = peek();
        RuleNode forTag = rule("for_tag");
        forTag.addToken(next());
        forTag.addToken(expectKeyword("for"));
        RuleNode targets = rule("loop_targets");
        targets.addToken(expect(JinjaTokenType.NAME, "a loop variable name"));
        while (atOp(",")) {
            targets.addToken(next());
            targets.addToken(expect(JinjaTokenType.NAME, "a loop variable name"));
        }
        forTag.add(targets);
        forTag.addToken(expectKeyword("in"));
        forTag.add(parseExpression());
        if (atKeyword("if")) throw syntax("Loop filtering ('for x in xs if ...') is not supported");
        forTag.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
        block.add(forTag);

        parseBodyInto(block, kind);
        if (atBlock("else")) throw syntax("'{% for %} ... {% else %}' is not supported");
        if (!atBlock("endfor")) {
            if (at(JinjaTokenType.TAG_CLOSE)) {
                throw error("SYN104", "UnclosedBlock", "Closing tag </" + peek().text()
                        + "> found inside the '{% for %}' opened at line " + begin.line()
                        + ": the element was opened outside the loop", peek().position());
            }
            throw error("SYN105", "UnclosedBlock",
                    "'{% for %}' opened at line " + begin.line() + " is not closed with '{% endfor %}'"
                            + (at(JinjaTokenType.EOF) ? "" : " (found '{% " + peek(1).text() + " %}')"),
                    at(JinjaTokenType.EOF) ? begin.position() : peek().position());
        }
        RuleNode endTag = rule("endfor_tag");
        endTag.addToken(next());
        endTag.addToken(next());
        endTag.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
        block.add(endTag);
        return block;
    }

    private RuleNode parseIfBlock(BodyKind kind) {
        String ruleName = kind == BodyKind.NODES ? "if_block" : kind == BodyKind.ATTRIBUTE_ITEMS ? "attr_if_block" : "value_if_block";
        RuleNode block = rule(ruleName);
        Token begin = peek();
        RuleNode ifTag = rule("if_tag");
        ifTag.addToken(next());
        ifTag.addToken(expectKeyword("if"));
        ifTag.add(parseExpression());
        ifTag.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
        block.add(ifTag);
        RuleNode body = rule("if_body");
        parseBodyInto(body, kind);
        block.add(body);

        while (atBlock("elif")) {
            RuleNode clause = rule("elif_clause");
            clause.addToken(next());
            clause.addToken(next());
            clause.add(parseExpression());
            clause.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
            parseBodyInto(clause, kind);
            block.add(clause);
        }
        if (atBlock("else")) {
            RuleNode clause = rule("else_clause");
            clause.addToken(next());
            clause.addToken(next());
            clause.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
            parseBodyInto(clause, kind);
            block.add(clause);
        }
        if (!atBlock("endif")) {
            if (at(JinjaTokenType.TAG_CLOSE)) {
                throw error("SYN104", "UnclosedBlock", "Closing tag </" + peek().text()
                        + "> found inside the '{% if %}' opened at line " + begin.line()
                        + ": the element was opened outside the block", peek().position());
            }
            throw error("SYN105", "UnclosedBlock",
                    "'{% if %}' opened at line " + begin.line() + " is not closed with '{% endif %}'"
                            + (at(JinjaTokenType.EOF) ? "" : " (found '{% " + peek(1).text() + " %}')"),
                    at(JinjaTokenType.EOF) ? begin.position() : peek().position());
        }
        RuleNode endTag = rule("endif_tag");
        endTag.addToken(next());
        endTag.addToken(next());
        endTag.addToken(expect(JinjaTokenType.BLOCK_END, "'%}'"));
        block.add(endTag);
        return block;
    }

    // ------------------------------------------------------------ expressions

    public RuleNode parseExpression() {
        RuleNode r = rule("expression");
        r.add(parseOrTest());
        if (atKeyword("if")) throw syntax("Inline if-expressions ('a if c else b') are not supported");
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
        r.add(parseAdditive());
        while (true) {
            if (atKeyword("is")) {
                RuleNode test = rule("test");
                test.addToken(next());
                if (atKeyword("not")) test.addToken(next());
                test.addToken(expect(JinjaTokenType.NAME, "a test name after 'is'"));
                if (atOp("(")) {
                    test.addToken(next());
                    if (!atOp(")")) test.add(parseCallArgs());
                    test.addToken(expectOp(")"));
                } else if (at(JinjaTokenType.NUMBER) || at(JinjaTokenType.STRING) || at(JinjaTokenType.NAME)) {
                    test.add(parseAtom());
                }
                r.add(test);
                continue;
            }
            RuleNode op = rule("comparison_operator");
            if (atOp("==") || atOp("!=") || atOp("<") || atOp(">") || atOp("<=") || atOp(">=")) {
                op.addToken(next());
            } else if (atKeyword("in")) {
                op.addToken(next());
            } else if (atKeyword("not") && peek(1).is(JinjaTokenType.KEYWORD, "in")) {
                op.addToken(next()); op.addToken(next());
            } else {
                break;
            }
            r.add(op);
            r.add(parseAdditive());
        }
        return r;
    }

    private RuleNode parseAdditive() {
        RuleNode r = rule("additive");
        r.add(parseMultiplicative());
        while (atOp("+") || atOp("-") || atOp("~")) { r.addToken(next()); r.add(parseMultiplicative()); }
        return r;
    }

    // يعالج الضرب والقسمة قبل المستوى الجمعي للمحافظة على أولوية التعابير داخل جينجا.
    private RuleNode parseMultiplicative() {
        RuleNode r = rule("multiplicative");
        r.add(parseUnary());
        while (atOp("*") || atOp("/") || atOp("//") || atOp("%")) { r.addToken(next()); r.add(parseUnary()); }
        return r;
    }

    private RuleNode parseUnary() {
        RuleNode r = rule("unary");
        if (atOp("-") || atOp("+")) { r.addToken(next()); r.add(parseUnary()); return r; }
        r.add(parsePostfix());
        return r;
    }

    private RuleNode parsePostfix() {
        RuleNode r = rule("postfix");
        r.add(parseAtom());
        while (atOp(".") || atOp("[") || atOp("(") || atOp("|")) {
            RuleNode trailer = rule("trailer");
            if (atOp(".")) {
                trailer.addToken(next());
                if (at(JinjaTokenType.NAME) || at(JinjaTokenType.KEYWORD)) trailer.addToken(next());
                else throw syntax("Expected a property name after '.'");
            } else if (atOp("[")) {
                trailer.addToken(next());
                trailer.add(parseExpression());
                trailer.addToken(expectOp("]"));
            } else if (atOp("(")) {
                trailer.addToken(next());
                if (!atOp(")")) trailer.add(parseCallArgs());
                trailer.addToken(expectOp(")"));
            } else {
                RuleNode filter = rule("filter");
                filter.addToken(next());
                filter.addToken(expect(JinjaTokenType.NAME, "a filter name after '|'"));
                if (atOp("(")) {
                    filter.addToken(next());
                    if (!atOp(")")) filter.add(parseCallArgs());
                    filter.addToken(expectOp(")"));
                }
                trailer.add(filter);
            }
            r.add(trailer);
        }
        return r;
    }

    private RuleNode parseCallArgs() {
        RuleNode r = rule("call_args");
        r.add(parseCallArg());
        while (atOp(",")) {
            r.addToken(next());
            if (atOp(")")) break;
            r.add(parseCallArg());
        }
        return r;
    }

    private RuleNode parseCallArg() {
        RuleNode r = rule("call_arg");
        if (at(JinjaTokenType.NAME) && peek(1).is(JinjaTokenType.OP, "=")) {
            r.addToken(next());
            r.addToken(next());
        }
        r.add(parseExpression());
        return r;
    }

    private RuleNode parseAtom() {
        RuleNode r = rule("atom");
        Token t = peek();
        if (t.is(JinjaTokenType.NAME) || t.is(JinjaTokenType.STRING) || t.is(JinjaTokenType.NUMBER)) {
            r.addToken(next());
            return r;
        }
        if (t.is(JinjaTokenType.KEYWORD)) {
            switch (t.text()) {
                case "true": case "false": case "none": case "True": case "False": case "None":
                    r.addToken(next());
                    return r;
                default:
                    throw syntax("Unexpected keyword '" + t.text() + "' in expression");
            }
        }
        if (atOp("[")) {
            RuleNode list = rule("list_literal");
            list.addToken(next());
            if (!atOp("]")) {
                list.add(parseExpression());
                while (atOp(",")) { list.addToken(next()); if (atOp("]")) break; list.add(parseExpression()); }
            }
            list.addToken(expectOp("]"));
            r.add(list);
            return r;
        }
        if (atOp("(")) {
            r.addToken(next());
            r.add(parseExpression());
            r.addToken(expectOp(")"));
            return r;
        }
        if (atOp("{")) throw syntax("Dictionary literals are not supported in template expressions");
        throw syntax("Unexpected " + describe(t) + " in expression");
    }
}
