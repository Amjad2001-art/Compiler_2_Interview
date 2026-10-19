package fjc.css;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.Token;
import fjc.parsetree.RuleNode;

import java.util.List;

/**
 * Recursive-descent CSS parser producing a parse tree.
 *
 * <pre>
 * stylesheet        := (qualified_rule | at_rule)* EOF
 * at_rule           := AT_KEYWORD prelude (';' | block)
 * block             := '{' (qualified_rule | at_rule | declaration_list) '}'
 * qualified_rule    := selector_list '{' declaration_list '}'
 * selector_list     := selector (',' selector)*
 * selector          := (any token except ',' '{' '}' ';')+
 * declaration_list  := (declaration ';'?)*
 * declaration       := IDENT ':' value ('!' 'important')?
 * value             := component_value+
 * component_value   := IDENT | NUMBER | DIMENSION | PERCENTAGE | STRING | HASH | URL | ',' | '/' | function
 * function          := FUNCTION component_value* ')'
 * </pre>
 */
public final class CssParser {
    private final List<Token> tokens;
    private int index = 0;

    public CssParser(List<Token> tokens) { this.tokens = tokens; }

    private Token peek() { return tokens.get(index); }
    private Token peek(int ahead) { return tokens.get(Math.min(index + ahead, tokens.size() - 1)); }
    private boolean at(CssTokenType t) { return peek().is(t); }
    private Token next() { Token t = peek(); if (!t.is(CssTokenType.EOF)) index++; return t; }
    private RuleNode rule(String name) { return new RuleNode(name, peek().position()); }

    private CompilerException syntax(String message) {
        return new CompilerException(ErrorCategory.SYNTAX, "SYN201", "CssSyntaxError", message, peek().position());
    }

    private Token expect(CssTokenType type, String what) {
        if (!at(type)) throw syntax("Expected " + what + " but found " + (at(CssTokenType.EOF) ? "end of file" : "'" + peek().text() + "'"));
        return next();
    }

    // يميز بين القواعد العادية والقواعد المبدوءة بعلامة خاصة ويبني شجرة قواعد ملف التنسيق.
    public RuleNode parseStylesheet() {
        RuleNode sheet = rule("stylesheet");
        while (!at(CssTokenType.EOF)) {
            if (at(CssTokenType.RBRACE)) throw syntax("Unexpected '}' (no matching '{')");
            sheet.add(at(CssTokenType.AT_KEYWORD) ? parseAtRule() : parseQualifiedRule());
        }
        sheet.addToken(next());
        return sheet;
    }

    private RuleNode parseAtRule() {
        RuleNode r = rule("at_rule");
        Token kw = expect(CssTokenType.AT_KEYWORD, "an at-rule");
        r.addToken(kw);
        RuleNode prelude = rule("prelude");
        while (!at(CssTokenType.LBRACE) && !at(CssTokenType.SEMICOLON) && !at(CssTokenType.EOF)) prelude.addToken(next());
        r.add(prelude);
        if (at(CssTokenType.SEMICOLON)) { r.addToken(next()); return r; }
        if (at(CssTokenType.EOF)) throw syntax("Unterminated @" + kw.text() + " rule");
        r.addToken(expect(CssTokenType.LBRACE, "'{'"));
        String name = kw.text().toLowerCase();
        if (name.equals("media") || name.equals("supports") || name.equals("keyframes") || name.equals("-webkit-keyframes")
                || name.equals("layer") || name.equals("container")) {
            RuleNode block = rule("rule_block");
            while (!at(CssTokenType.RBRACE) && !at(CssTokenType.EOF)) {
                block.add(at(CssTokenType.AT_KEYWORD) ? parseAtRule() : parseQualifiedRule());
            }
            r.add(block);
        } else {
            r.add(parseDeclarationList());
        }
        r.addToken(expect(CssTokenType.RBRACE, "'}'"));
        return r;
    }

    private RuleNode parseQualifiedRule() {
        RuleNode r = rule("qualified_rule");
        RuleNode selectors = rule("selector_list");
        RuleNode selector = rule("selector");
        while (!at(CssTokenType.LBRACE) && !at(CssTokenType.EOF)) {
            if (at(CssTokenType.SEMICOLON) || at(CssTokenType.RBRACE)) {
                throw syntax("Unexpected '" + peek().text() + "' in selector (missing '{'?)");
            }
            if (at(CssTokenType.COMMA)) {
                if (selector.size() == 0) throw syntax("Empty selector before ','");
                selectors.add(selector);
                selectors.addToken(next());
                selector = rule("selector");
                continue;
            }
            selector.addToken(next());
        }
        if (selector.size() == 0) throw syntax("Empty selector before '{'");
        selectors.add(selector);
        r.add(selectors);
        r.addToken(expect(CssTokenType.LBRACE, "'{'"));
        r.add(parseDeclarationList());
        r.addToken(expect(CssTokenType.RBRACE, "'}'"));
        return r;
    }

    private RuleNode parseDeclarationList() {
        RuleNode list = rule("declaration_list");
        while (!at(CssTokenType.RBRACE) && !at(CssTokenType.EOF)) {
            if (at(CssTokenType.SEMICOLON)) { list.addToken(next()); continue; }
            list.add(parseDeclaration());
            if (at(CssTokenType.SEMICOLON)) list.addToken(next());
            else if (!at(CssTokenType.RBRACE)) throw syntax("Expected ';' after declaration");
        }
        return list;
    }

    // يتحقق من بنية الخاصية والقيمة داخل قاعدة التنسيق؛ المثال: لون يليه قيمة ثم فاصلة منقوطة.
    private RuleNode parseDeclaration() {
        RuleNode d = rule("declaration");
        if (!at(CssTokenType.IDENT)) throw syntax("Expected a property name but found '" + peek().text() + "'");
        d.addToken(next());
        d.addToken(expect(CssTokenType.COLON, "':' after property name"));
        RuleNode value = rule("value");
        int depth = 0;
        while (!at(CssTokenType.EOF)) {
            if (depth == 0 && (at(CssTokenType.SEMICOLON) || at(CssTokenType.RBRACE))) break;
            if (depth == 0 && at(CssTokenType.DELIM) && peek().text().equals("!")
                    && peek(1).is(CssTokenType.IDENT) && peek(1).text().equalsIgnoreCase("important")) {
                break;
            }
            Token t = next();
            if (t.is(CssTokenType.FUNCTION) || t.is(CssTokenType.LPAREN)) depth++;
            if (t.is(CssTokenType.RPAREN)) depth--;
            if (depth < 0) throw syntax("Unexpected ')' in value");
            value.addToken(t);
        }
        if (depth != 0) throw syntax("Unbalanced parentheses in value");
        if (value.size() == 0) throw syntax("Empty value for property '" + d.firstToken().text() + "'");
        d.add(value);
        if (at(CssTokenType.DELIM) && peek().text().equals("!")) {
            RuleNode important = rule("important");
            important.addToken(next());
            important.addToken(next());
            d.add(important);
        }
        return d;
    }
}
