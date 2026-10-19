package fjc.jinja.lexer;

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
 * Mode-based lexer for Jinja templates that contain HTML.
 *
 * <ul>
 *   <li><b>DATA</b> mode: text, tags, comments, doctype and Jinja delimiters.</li>
 *   <li><b>TAG</b> mode (inside {@code <a ...>}): attribute names, '=', quotes, '>' and '/>'.
 *       Jinja delimiters are allowed here too ({@code <option {% if ... %}selected{% endif %}>}).</li>
 *   <li><b>ATTR_VALUE</b> mode (inside quotes): literal text and Jinja output expressions.</li>
 *   <li><b>RAW</b> mode (inside {@code <script>} / {@code <style>}): text until the closing tag.</li>
 *   <li><b>EXPR</b> mode (inside {@code {{ }}} / {@code {% %}}): names, keywords, strings, numbers, operators.</li>
 * </ul>
 */
public final class JinjaLexer {

    public static final Set<String> KEYWORDS = Set.of(
            "for", "in", "endfor", "if", "elif", "else", "endif", "not", "and", "or", "is",
            "true", "false", "none", "True", "False", "None");

    private static final String[] OPERATORS = {
            "==", "!=", "<=", ">=", "//", "<", ">", "+", "-", "*", "/", "%", "~", "|",
            "(", ")", "[", "]", ",", ".", ":", "=", "{", "}"
    };

    private enum Mode { DATA, TAG, ATTR_VALUE, RAW, EXPR }

    private final String fileName;
    private final String src;
    private int pos = 0;
    private int line = 1;
    private int column = 1;
    private final List<Token> tokens = new ArrayList<>();

    private Mode mode = Mode.DATA;
    private final Deque<Mode> returnModes = new ArrayDeque<>();
    private String exprEnd = null;
    private char quote = 0;
    private String currentTag = null;
    private String rawEndTag = null;

    public JinjaLexer(SourceFile file) {
        this.fileName = file.name();
        this.src = file.text();
    }

    public JinjaLexer(String fileName, String text) {
        this(new SourceFile(fileName, text));
    }

    // يميز هذا المعجم بين النص العادي ووسوم إظهار القيم ووسوم التحكم وعناصر الواجهة.
    public List<Token> tokenize() {
        while (pos < src.length()) {
            switch (mode) {
                case DATA: lexData(); break;
                case TAG: lexTag(); break;
                case ATTR_VALUE: lexAttrValue(); break;
                case RAW: lexRaw(); break;
                case EXPR: lexExpr(); break;
                default: throw new IllegalStateException();
            }
        }
        if (mode == Mode.EXPR) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX101", "UnterminatedJinjaTag",
                    "Unterminated Jinja delimiter: expected '" + exprEnd + "' before end of file", position());
        }
        if (mode == Mode.TAG || mode == Mode.ATTR_VALUE) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX102", "UnterminatedHtmlTag",
                    "Unterminated HTML start tag <" + currentTag + "> at end of file", position());
        }
        tokens.add(new Token(JinjaTokenType.EOF, "", position()));
        return tokens;
    }

    // ------------------------------------------------------------------ utils

    private SourcePosition position() { return new SourcePosition(fileName, line, column); }

    private boolean startsWith(String s) { return src.startsWith(s, pos); }

    private char peekChar(int ahead) {
        int p = pos + ahead;
        return p < src.length() ? src.charAt(p) : '\0';
    }

    private void advance(int n) {
        for (int i = 0; i < n && pos < src.length(); i++) {
            if (src.charAt(pos) == '\n') { line++; column = 1; } else column++;
            pos++;
        }
    }

    private void emit(JinjaTokenType type, String text, SourcePosition at) {
        tokens.add(new Token(type, text, at));
    }

    private void enterExpr(String begin, String end, Mode returnTo) {
        SourcePosition at = position();
        advance(begin.length());
        if (peekChar(0) == '-') advance(1);   // whitespace-control marker, accepted and ignored
        emit(begin.equals("{{") ? JinjaTokenType.VAR_BEGIN : JinjaTokenType.BLOCK_BEGIN, begin, at);
        returnModes.push(returnTo);
        exprEnd = end;
        mode = Mode.EXPR;
    }

    private static boolean isNameStart(char c) { return Character.isLetter(c) || c == '_'; }
    private static boolean isNameChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
    private static boolean isTagNameChar(char c) { return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':'; }

    // ------------------------------------------------------------------ DATA

    // الأولوية لحدود التعابير والتعليقات ووسوم القالب قبل اعتبار المحتوى نصا عاديا.
    private void lexData() {
        SourcePosition at = position();
        if (startsWith("{{")) { enterExpr("{{", "}}", Mode.DATA); return; }
        if (startsWith("{%")) { enterExpr("{%", "%}", Mode.DATA); return; }
        if (startsWith("{#")) { skipComment(); return; }
        if (startsWith("<!--")) {
            int end = src.indexOf("-->", pos + 4);
            if (end < 0) throw new CompilerException(ErrorCategory.LEXICAL, "LEX103", "UnterminatedHtmlComment",
                    "Unterminated HTML comment", at);
            String text = src.substring(pos + 4, end);
            advance(end + 3 - pos);
            emit(JinjaTokenType.HTML_COMMENT, text, at);
            return;
        }
        if (startsWith("<!")) {
            int end = src.indexOf('>', pos);
            if (end < 0) throw new CompilerException(ErrorCategory.LEXICAL, "LEX104", "UnterminatedDoctype",
                    "Unterminated <! declaration", at);
            String text = src.substring(pos + 2, end).trim();
            advance(end + 1 - pos);
            emit(JinjaTokenType.DOCTYPE, text, at);
            return;
        }
        if (startsWith("</") && isNameStart(peekChar(2))) {
            advance(2);
            int start = pos;
            while (pos < src.length() && isTagNameChar(src.charAt(pos))) advance(1);
            String name = src.substring(start, pos).toLowerCase();
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) advance(1);
            if (peekChar(0) != '>') throw new CompilerException(ErrorCategory.LEXICAL, "LEX105", "MalformedClosingTag",
                    "Expected '>' to finish closing tag </" + name + ">", position());
            advance(1);
            emit(JinjaTokenType.TAG_CLOSE, name, at);
            return;
        }
        if (peekChar(0) == '<' && isNameStart(peekChar(1))) {
            advance(1);
            int start = pos;
            while (pos < src.length() && isTagNameChar(src.charAt(pos))) advance(1);
            currentTag = src.substring(start, pos).toLowerCase();
            emit(JinjaTokenType.TAG_OPEN, currentTag, at);
            mode = Mode.TAG;
            return;
        }
        // plain text: run until the next interesting construct
        int start = pos;
        while (pos < src.length()) {
            if (startsWith("{{") || startsWith("{%") || startsWith("{#")) break;
            if (src.charAt(pos) == '<' && (isNameStart(peekChar(1)) || startsWith("</") || startsWith("<!"))) break;
            advance(1);
        }
        if (pos == start) advance(1);   // lone '<' or similar: treat as text
        emit(JinjaTokenType.TEXT, src.substring(start, pos), at);
    }

    private void skipComment() {
        SourcePosition at = position();
        int end = src.indexOf("#}", pos + 2);
        if (end < 0) throw new CompilerException(ErrorCategory.LEXICAL, "LEX106", "UnterminatedJinjaComment",
                "Unterminated Jinja comment '{#'", at);
        advance(end + 2 - pos);
    }

    // ------------------------------------------------------------------- RAW

    private void lexRaw() {
        SourcePosition at = position();
        if (startsWith("{{")) { enterExpr("{{", "}}", Mode.RAW); return; }
        if (startsWith("{%")) { enterExpr("{%", "%}", Mode.RAW); return; }
        String closing = "</" + rawEndTag;
        int start = pos;
        while (pos < src.length()) {
            if (src.regionMatches(true, pos, closing, 0, closing.length())) {
                mode = Mode.DATA;
                break;
            }
            if (startsWith("{{") || startsWith("{%")) break;
            advance(1);
        }
        if (pos > start) emit(JinjaTokenType.TEXT, src.substring(start, pos), at);
        if (pos >= src.length()) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX107", "UnterminatedRawElement",
                    "Missing closing tag </" + rawEndTag + ">", at);
        }
    }

    // ------------------------------------------------------------------- TAG

    // بعد اكتشاف وسم واجهة، يفصل اسم العنصر وخصائصه لإتاحتها لقواعد الشجرة.
    private void lexTag() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) advance(1);
        if (pos >= src.length()) return;
        SourcePosition at = position();
        if (startsWith("{{")) { enterExpr("{{", "}}", Mode.TAG); return; }
        if (startsWith("{%")) { enterExpr("{%", "%}", Mode.TAG); return; }
        if (startsWith("{#")) { skipComment(); return; }
        if (startsWith("/>")) {
            advance(2);
            emit(JinjaTokenType.TAG_SELF_CLOSE, "/>", at);
            mode = Mode.DATA;
            return;
        }
        char c = src.charAt(pos);
        if (c == '>') {
            advance(1);
            emit(JinjaTokenType.TAG_END, ">", at);
            if (currentTag.equals("script") || currentTag.equals("style")) {
                rawEndTag = currentTag;
                mode = Mode.RAW;
            } else {
                mode = Mode.DATA;
            }
            return;
        }
        if (c == '=') {
            advance(1);
            emit(JinjaTokenType.ATTR_EQ, "=", at);
            // unquoted value?
            while (pos < src.length() && (src.charAt(pos) == ' ' || src.charAt(pos) == '\t')) advance(1);
            char n = peekChar(0);
            if (n != '"' && n != '\'' && n != '>' && !startsWith("{{") && !startsWith("{%") && n != '\0') {
                SourcePosition vat = position();
                int start = pos;
                while (pos < src.length() && !Character.isWhitespace(src.charAt(pos)) && src.charAt(pos) != '>'
                        && !startsWith("/>")) advance(1);
                emit(JinjaTokenType.ATTR_UNQUOTED, src.substring(start, pos), vat);
            }
            return;
        }
        if (c == '"' || c == '\'') {
            quote = c;
            advance(1);
            emit(JinjaTokenType.ATTR_VALUE_START, String.valueOf(c), at);
            mode = Mode.ATTR_VALUE;
            return;
        }
        if (c == '<') {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX102", "UnterminatedHtmlTag",
                    "Unexpected '<' inside start tag <" + currentTag + ">: missing '>'?", at);
        }
        if (c == '/') {          // stray '/' not followed by '>' (HTML tolerates it)
            advance(1);
            return;
        }
        int start = pos;
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (Character.isWhitespace(ch) || ch == '=' || ch == '>' || ch == '"' || ch == '\'' || ch == '/'
                    || startsWith("{{") || startsWith("{%")) break;
            advance(1);
        }
        if (pos == start) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX108", "IllegalCharacter",
                    "Illegal character '" + c + "' inside start tag <" + currentTag + ">", at);
        }
        emit(JinjaTokenType.ATTR_NAME, src.substring(start, pos), at);
    }

    // ------------------------------------------------------------ ATTR_VALUE

    private void lexAttrValue() {
        SourcePosition at = position();
        if (startsWith("{{")) { enterExpr("{{", "}}", Mode.ATTR_VALUE); return; }
        if (startsWith("{%")) { enterExpr("{%", "%}", Mode.ATTR_VALUE); return; }
        if (src.charAt(pos) == quote) {
            advance(1);
            emit(JinjaTokenType.ATTR_VALUE_END, String.valueOf(quote), at);
            mode = Mode.TAG;
            return;
        }
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != quote && !startsWith("{{") && !startsWith("{%")) advance(1);
        if (pos >= src.length()) {
            throw new CompilerException(ErrorCategory.LEXICAL, "LEX109", "UnterminatedAttributeValue",
                    "Unterminated attribute value in <" + currentTag + ">", at);
        }
        emit(JinjaTokenType.ATTR_TEXT, src.substring(start, pos), at);
    }

    // ------------------------------------------------------------------ EXPR

    // داخل التعبير تُقرأ أسماء المتغيرات والمعاملات والمرشحات بدلا من التعامل معها كنص واجهة.
    private void lexExpr() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) advance(1);
        if (pos >= src.length()) return;
        SourcePosition at = position();
        if (startsWith("-" + exprEnd)) advance(1);
        if (startsWith(exprEnd)) {
            advance(exprEnd.length());
            emit(exprEnd.equals("}}") ? JinjaTokenType.VAR_END : JinjaTokenType.BLOCK_END, exprEnd, at);
            mode = returnModes.pop();
            exprEnd = null;
            return;
        }
        char c = src.charAt(pos);
        if (c == '"' || c == '\'') { lexString(c, at); return; }
        if (Character.isDigit(c)) {
            int start = pos;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance(1);
            if (peekChar(0) == '.' && Character.isDigit(peekChar(1))) {
                advance(1);
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance(1);
            }
            emit(JinjaTokenType.NUMBER, src.substring(start, pos), at);
            return;
        }
        if (isNameStart(c)) {
            int start = pos;
            while (pos < src.length() && isNameChar(src.charAt(pos))) advance(1);
            String word = src.substring(start, pos);
            emit(KEYWORDS.contains(word) ? JinjaTokenType.KEYWORD : JinjaTokenType.NAME, word, at);
            return;
        }
        for (String op : OPERATORS) {
            if (startsWith(op)) {
                advance(op.length());
                emit(JinjaTokenType.OP, op, at);
                return;
            }
        }
        throw new CompilerException(ErrorCategory.LEXICAL, "LEX110", "IllegalCharacter",
                "Illegal character '" + c + "' inside Jinja expression", at);
    }

    private void lexString(char q, SourcePosition at) {
        advance(1);
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length() || startsWith(exprEnd)) {
                throw new CompilerException(ErrorCategory.LEXICAL, "LEX111", "UnterminatedString",
                        "Unterminated string literal inside Jinja expression", at);
            }
            char c = src.charAt(pos);
            if (c == q) { advance(1); break; }
            if (c == '\\' && pos + 1 < src.length()) {
                char e = src.charAt(pos + 1);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '\\': sb.append('\\'); break;
                    case '\'': sb.append('\''); break;
                    case '"': sb.append('"'); break;
                    default: sb.append('\\').append(e);
                }
                advance(2);
                continue;
            }
            sb.append(c);
            advance(1);
        }
        emit(JinjaTokenType.STRING, sb.toString(), at);
    }
}
