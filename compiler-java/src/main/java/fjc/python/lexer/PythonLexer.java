package fjc.python.lexer;

import fjc.common.CompilerException;
import fjc.common.ErrorCategory;
import fjc.common.SourceFile;
import fjc.common.SourcePosition;
import fjc.common.Token;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Lexical analyser for the supported Python subset.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>identifiers, keywords, numbers, strings (single/double/triple quoted, escapes)</li>
 *   <li>operators and punctuation</li>
 *   <li>comments (skipped) and blank lines (skipped)</li>
 *   <li>NEWLINE / INDENT / DEDENT tokens (Python's block structure)</li>
 *   <li>implicit line joining inside ( [ { and explicit joining with a backslash</li>
 * </ul>
 * Lexical errors are thrown as {@link CompilerException} with line/column.
 */
public final class PythonLexer {

    public static final Set<String> KEYWORDS = Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield");

    private static final String[] OPERATORS = {
            "//=", "**=", "==", "!=", "<=", ">=", "//", "**", "+=", "-=", "*=", "/=", "%=", "->",
            "(", ")", "[", "]", "{", "}", ",", ":", ".", ";", "=", "<", ">", "+", "-", "*", "/", "%", "@", "|", "&", "~", "^"
    };

    private final String fileName;
    private final String src;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    private final List<Token> tokens = new ArrayList<>();
    private final Deque<Integer> indentStack = new ArrayDeque<>();
    private int bracketDepth = 0;
    private boolean atLineStart = true;
    private boolean lineHasTokens = false;

    public PythonLexer(SourceFile file) {
        this.fileName = file.name();
        this.src = file.text();
        indentStack.push(0);
    }

    public PythonLexer(String fileName, String text) {
        this(new SourceFile(fileName, text));
    }

    // هذه نقطة الدخول للمعجم: يقرأ الأحرف ويخرج رموزا مع السطر والعمود؛ مثال: تعريف الدالة يتحول إلى رموز معروفة.
    public List<Token> tokenize() {
        while (true) {
            if (atLineStart && bracketDepth == 0) {
                // blank and comment-only lines are skipped completely; loop until a real line starts
                while (atLineStart && pos < src.length()) handleIndentation();
                if (pos >= src.length()) break;
            }
            if (pos >= src.length()) break;
            char c = src.charAt(pos);
            if (c == '\n') {
                consumeNewline();
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\f') {
                advance();
                continue;
            }
            if (c == '#') {
                while (pos < src.length() && src.charAt(pos) != '\n') advance();
                continue;
            }
            if (c == '\\' && pos + 1 < src.length() && src.charAt(pos + 1) == '\n') {
                advance();
                advance();
                line++;
                column = 1;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                lexNameOrString();
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
                lexNumber();
                continue;
            }
            if (c == '"' || c == '\'') {
                lexString("", position());
                continue;
            }
            if (!lexOperator()) {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX001", "IllegalCharacter",
                        "Illegal character '" + c + "' in Python source", position());
            }
        }
        if (lineHasTokens) {
            emit(PythonTokenType.NEWLINE, "\n");
        }
        while (indentStack.size() > 1) {
            indentStack.pop();
            emit(PythonTokenType.DEDENT, "");
        }
        emit(PythonTokenType.EOF, "");
        return tokens;
    }

    // ------------------------------------------------------------------ helpers

    private SourcePosition position() { return new SourcePosition(fileName, line, column); }

    private void emit(PythonTokenType type, String text) {
        tokens.add(new Token(type, text, position()));
    }

    private void emit(PythonTokenType type, String text, SourcePosition at) {
        tokens.add(new Token(type, text, at));
        lineHasTokens = true;
    }

    private void advance() {
        pos++;
        column++;
    }

    private void consumeNewline() {
        boolean logicalLineEnd = bracketDepth == 0;
        if (logicalLineEnd && lineHasTokens) {
            tokens.add(new Token(PythonTokenType.NEWLINE, "\n", position()));
        }
        pos++;
        line++;
        column = 1;
        if (logicalLineEnd) {          // inside ( [ { the logical line continues (implicit joining)
            atLineStart = true;
            lineHasTokens = false;
        }
    }

    /** Measures indentation of the new logical line and emits INDENT/DEDENT. */
    // مسافات بداية السطر في بايثون تحدد الكتل، لذلك يولد المحلل رموز الدخول والخروج من مستوى الإزاحة.
    private void handleIndentation() {
        int width = 0;
        int p = pos;
        while (p < src.length()) {
            char c = src.charAt(p);
            if (c == ' ') width++;
            else if (c == '\t') width = (width / 8 + 1) * 8;
            else break;
            p++;
        }
        // blank line or comment-only line: skip entirely without tokens
        if (p >= src.length()) {
            column += (p - pos);
            pos = p;
            atLineStart = false;
            return;
        }
        char c = src.charAt(p);
        if (c == '\n' || c == '#') {
            column += (p - pos);
            pos = p;
            if (c == '#') {
                while (pos < src.length() && src.charAt(pos) != '\n') advance();
            }
            if (pos < src.length()) {
                pos++;
                line++;
                column = 1;
            }
            atLineStart = true;
            lineHasTokens = false;
            return;
        }
        column += (p - pos);
        pos = p;
        atLineStart = false;
        int current = indentStack.peek();
        SourcePosition at = position();
        if (width > current) {
            indentStack.push(width);
            tokens.add(new Token(PythonTokenType.INDENT, "", at));
        } else {
            while (width < indentStack.peek()) {
                indentStack.pop();
                tokens.add(new Token(PythonTokenType.DEDENT, "", at));
            }
            if (width != indentStack.peek()) {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX003", "InconsistentIndentation",
                        "Unindent does not match any outer indentation level", at);
            }
        }
    }

    // يحدد هل التسلسل اسم أو كلمة محجوزة أو بداية نص؛ ترتيب التمييز مهم حتى لا تُقرأ الكلمات المحجوزة كمعرفات.
    private void lexNameOrString() {
        SourcePosition at = position();
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) advance();
        String word = src.substring(start, pos);
        // string prefixes: r"..." u"..." (f/b are unsupported)
        if (pos < src.length() && (src.charAt(pos) == '"' || src.charAt(pos) == '\'')
                && word.length() <= 2 && word.chars().allMatch(ch -> "rRuUfFbB".indexOf(ch) >= 0)) {
            String prefix = word.toLowerCase();
            if (prefix.contains("f") || prefix.contains("b")) {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX004", "UnsupportedStringPrefix",
                        "String prefix '" + word + "' is not supported by the Python subset (use plain strings)", at);
            }
            lexString(prefix, at);
            return;
        }
        if (KEYWORDS.contains(word)) emit(PythonTokenType.KEYWORD, word, at);
        else emit(PythonTokenType.NAME, word, at);
    }

    private void lexNumber() {
        SourcePosition at = position();
        int start = pos;
        boolean isFloat = false;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            advance();
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            int save = pos;
            int saveCol = column;
            advance();
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) advance();
            if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                isFloat = true;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
            } else {
                pos = save;
                column = saveCol;
            }
        }
        if (pos < src.length() && (Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '_')) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX005", "InvalidNumber",
                    "Invalid number literal '" + src.substring(start, pos + 1) + "'", at);
        }
        String text = src.substring(start, pos);
        emit(PythonTokenType.NUMBER, isFloat ? text : text, at);
    }

    private void lexString(String prefix, SourcePosition at) {
        boolean raw = prefix.contains("r");
        char quote = src.charAt(pos);
        boolean triple = pos + 2 < src.length() && src.charAt(pos + 1) == quote && src.charAt(pos + 2) == quote;
        if (triple) { advance(); advance(); advance(); } else advance();
        StringBuilder value = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX002", "UnterminatedString",
                        "Unterminated string literal", at);
            }
            char c = src.charAt(pos);
            if (triple) {
                if (c == quote && pos + 2 < src.length() && src.charAt(pos + 1) == quote && src.charAt(pos + 2) == quote) {
                    advance(); advance(); advance();
                    break;
                }
            } else if (c == quote) {
                advance();
                break;
            } else if (c == '\n') {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX002", "UnterminatedString",
                        "Unterminated string literal (newline inside single-quoted string)", at);
            }
            if (c == '\\' && !raw) {
                advance();
                if (pos >= src.length()) continue;
                char e = src.charAt(pos);
                switch (e) {
                    case 'n': value.append('\n'); advance(); break;
                    case 't': value.append('\t'); advance(); break;
                    case 'r': value.append('\r'); advance(); break;
                    case '0': value.append('\0'); advance(); break;
                    case '\\': value.append('\\'); advance(); break;
                    case '\'': value.append('\''); advance(); break;
                    case '"': value.append('"'); advance(); break;
                    case '\n': advance(); line++; column = 1; break;
                    case 'x': {
                        advance();
                        String hex = src.substring(pos, Math.min(pos + 2, src.length()));
                        value.append((char) Integer.parseInt(hex, 16));
                        advance(); advance();
                        break;
                    }
                    case 'u': {
                        advance();
                        String hex = src.substring(pos, Math.min(pos + 4, src.length()));
                        value.append((char) Integer.parseInt(hex, 16));
                        for (int i = 0; i < 4; i++) advance();
                        break;
                    }
                    default: value.append('\\').append(e); advance();
                }
                continue;
            }
            if (c == '\n') {
                value.append(c);
                pos++;
                line++;
                column = 1;
                continue;
            }
            value.append(c);
            advance();
        }
        emit(PythonTokenType.STRING, value.toString(), at);
    }

    // يفحص المعاملات الأطول قبل الأقصر حتى لا تتجزأ المقارنة المركبة إلى معاملين منفصلين.
    private boolean lexOperator() {
        SourcePosition at = position();
        for (String op : OPERATORS) {
            if (src.startsWith(op, pos)) {
                for (int i = 0; i < op.length(); i++) advance();
                switch (op) {
                    case "(": case "[": case "{": bracketDepth++; break;
                    case ")": case "]": case "}": bracketDepth = Math.max(0, bracketDepth - 1); break;
                    default: break;
                }
                emit(PythonTokenType.OP, op, at);
                return true;
            }
        }
        return false;
    }
}
