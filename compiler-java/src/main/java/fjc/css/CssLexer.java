package fjc.css;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourceFile;
import fjc.common.SourcePosition;
import fjc.common.Token;

import java.util.ArrayList;
import java.util.List;

/** Lexical analyser for CSS stylesheets (comments skipped, whitespace collapsed away). */
public final class CssLexer {
    private final String fileName;
    private final String src;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    private final List<Token> tokens = new ArrayList<>();

    public CssLexer(SourceFile file) {
        this.fileName = file.name();
        this.src = file.text();
    }

    public CssLexer(String fileName, String text) {
        this(new SourceFile(fileName, text));
    }

    // يحوّل ملف التنسيق إلى رموز للمحددات والأقواس والتصريحات بدلا من نسخه أثناء التحليل.
    public List<Token> tokenize() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            SourcePosition at = position();
            if (Character.isWhitespace(c)) { advance(1); continue; }
            if (src.startsWith("/*", pos)) {
                int end = src.indexOf("*/", pos + 2);
                if (end < 0) throw new CompilerException(ErrorCategory.LEXICAL, "LEX201", "UnterminatedComment",
                        "Unterminated CSS comment", at);
                advance(end + 2 - pos);
                continue;
            }
            if (c == '"' || c == '\'') { lexString(c, at); continue; }
            if (c == '#') {
                advance(1);
                int start = pos;
                while (pos < src.length() && isNameChar(src.charAt(pos))) advance(1);
                emit(CssTokenType.HASH, src.substring(start, pos), at);
                continue;
            }
            if (c == '@') {
                advance(1);
                int start = pos;
                while (pos < src.length() && isNameChar(src.charAt(pos))) advance(1);
                if (pos == start) throw new CompilerException(ErrorCategory.LEXICAL, "LEX202", "IllegalCharacter",
                        "Expected an at-rule name after '@'", at);
                emit(CssTokenType.AT_KEYWORD, src.substring(start, pos), at);
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))
                    || ((c == '-' || c == '+') && pos + 1 < src.length()
                        && (Character.isDigit(src.charAt(pos + 1)) || (src.charAt(pos + 1) == '.' && pos + 2 < src.length() && Character.isDigit(src.charAt(pos + 2)))))) {
                lexNumber(at);
                continue;
            }
            if (isNameStart(c) || (c == '-' && pos + 1 < src.length() && (isNameStart(src.charAt(pos + 1)) || src.charAt(pos + 1) == '-'))) {
                int start = pos;
                while (pos < src.length() && isNameChar(src.charAt(pos))) advance(1);
                String name = src.substring(start, pos);
                if (pos < src.length() && src.charAt(pos) == '(') {
                    advance(1);
                    if (name.equalsIgnoreCase("url")) {
                        int p = pos;
                        while (p < src.length() && Character.isWhitespace(src.charAt(p))) p++;
                        if (p < src.length() && src.charAt(p) != '"' && src.charAt(p) != '\'') {
                            int end = src.indexOf(')', p);
                            if (end < 0) throw new CompilerException(ErrorCategory.LEXICAL, "LEX203", "UnterminatedUrl",
                                    "Unterminated url(", at);
                            String url = src.substring(p, end).trim();
                            advance(end + 1 - pos);
                            emit(CssTokenType.URL, url, at);
                            continue;
                        }
                    }
                    emit(CssTokenType.FUNCTION, name, at);
                } else {
                    emit(CssTokenType.IDENT, name, at);
                }
                continue;
            }
            advance(1);
            switch (c) {
                case ':': emit(CssTokenType.COLON, ":", at); break;
                case ';': emit(CssTokenType.SEMICOLON, ";", at); break;
                case ',': emit(CssTokenType.COMMA, ",", at); break;
                case '{': emit(CssTokenType.LBRACE, "{", at); break;
                case '}': emit(CssTokenType.RBRACE, "}", at); break;
                case '(': emit(CssTokenType.LPAREN, "(", at); break;
                case ')': emit(CssTokenType.RPAREN, ")", at); break;
                case '[': emit(CssTokenType.LBRACKET, "[", at); break;
                case ']': emit(CssTokenType.RBRACKET, "]", at); break;
                default:
                    if ("*>+~.=!/%^$|&-".indexOf(c) >= 0) emit(CssTokenType.DELIM, String.valueOf(c), at);
                    else throw new CompilerException(ErrorCategory.LEXICAL, "LEX202", "IllegalCharacter",
                            "Illegal character '" + c + "' in stylesheet", at);
            }
        }
        tokens.add(new Token(CssTokenType.EOF, "", position()));
        return tokens;
    }

    private static boolean isNameStart(char c) { return Character.isLetter(c) || c == '_' || c > 127; }
    private static boolean isNameChar(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c > 127; }

    private SourcePosition position() { return new SourcePosition(fileName, line, column); }

    private void advance(int n) {
        for (int i = 0; i < n && pos < src.length(); i++) {
            if (src.charAt(pos) == '\n') { line++; column = 1; } else column++;
            pos++;
        }
    }

    private void emit(CssTokenType type, String text, SourcePosition at) {
        tokens.add(new Token(type, text, at));
    }

    private void lexString(char q, SourcePosition at) {
        advance(1);
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length() || src.charAt(pos) == '\n') {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX204", "UnterminatedString",
                        "Unterminated string in stylesheet", at);
            }
            char c = src.charAt(pos);
            if (c == q) { advance(1); break; }
            if (c == '\\' && pos + 1 < src.length()) { sb.append(src.charAt(pos + 1)); advance(2); continue; }
            sb.append(c);
            advance(1);
        }
        emit(CssTokenType.STRING, sb.toString(), at);
    }

    private void lexNumber(SourcePosition at) {
        int start = pos;
        if (src.charAt(pos) == '-' || src.charAt(pos) == '+') advance(1);
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance(1);
        if (pos < src.length() && src.charAt(pos) == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
            advance(1);
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance(1);
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')
                && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
            advance(1);
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance(1);
        }
        String number = src.substring(start, pos);
        if (pos < src.length() && src.charAt(pos) == '%') {
            advance(1);
            emit(CssTokenType.PERCENTAGE, number + "%", at);
            return;
        }
        int unitStart = pos;
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) advance(1);
        if (pos > unitStart) emit(CssTokenType.DIMENSION, src.substring(start, pos), at);
        else emit(CssTokenType.NUMBER, number, at);
    }
}
