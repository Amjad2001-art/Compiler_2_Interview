package fjc.codegen;

import fjc.common.SourcePosition;
import fjc.evaluate.BooleanValue;
import fjc.evaluate.DictValue;
import fjc.evaluate.Evaluator;
import fjc.evaluate.ListValue;
import fjc.evaluate.NoneValue;
import fjc.evaluate.NumberValue;
import fjc.evaluate.StringValue;
import fjc.evaluate.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Implementations of the Jinja filters the semantic analyser accepts. */
public final class JinjaFilters {
    private final Evaluator runtime;

    public JinjaFilters(Evaluator runtime) { this.runtime = runtime; }

    public Value apply(String name, Value target, List<Value> args, Map<String, Value> kwargs, SourcePosition pos) {
        switch (name) {
            case "length": case "count": return NumberValue.ofLong(length(target, pos));
            case "wordcount": return NumberValue.ofLong(target.asText().trim().isEmpty() ? 0 : target.asText().trim().split("\\s+").length);
            case "upper": return new StringValue(target.asText().toUpperCase());
            case "lower": return new StringValue(target.asText().toLowerCase());
            case "capitalize": {
                String s = target.asText();
                return new StringValue(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase());
            }
            case "title": return new StringValue(title(target.asText()));
            case "trim": return new StringValue(target.asText().trim());
            case "string": return new StringValue(target.asText());
            case "escape": case "e": case "forceescape": return new StringValue(HtmlEscaper.escape(target.asText()), true);
            case "safe": return new StringValue(target.asText(), true);
            case "striptags": return new StringValue(target.asText().replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim());
            case "urlencode": return new StringValue(java.net.URLEncoder.encode(target.asText(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
            case "truncate": return new StringValue(truncate(target.asText(), args, kwargs, pos));
            case "default": case "d": {
                boolean strict = argOr(args, kwargs, 1, "boolean", BooleanValue.FALSE).isTruthy();
                Value fallback = argOr(args, kwargs, 0, "default_value", new StringValue(""));
                if (target.isNone() || (strict && !target.isTruthy())) return fallback;
                return target;
            }
            case "round": {
                double v = runtime.asNumber(target, pos).doubleValue();
                int precision = (int) numberArg(argOr(args, kwargs, 0, "precision", NumberValue.ofLong(0)), pos);
                String method = argOr(args, kwargs, 1, "method", new StringValue("common")).asText();
                double factor = Math.pow(10, precision);
                double r;
                switch (method) {
                    case "ceil": r = Math.ceil(v * factor) / factor; break;
                    case "floor": r = Math.floor(v * factor) / factor; break;
                    default: r = Math.round(v * factor) / factor;
                }
                return NumberValue.ofDouble(r);
            }
            case "int": {
                if (target instanceof NumberValue) return NumberValue.ofLong(((NumberValue) target).longValue());
                try { return NumberValue.ofLong((long) Double.parseDouble(target.asText().trim())); }
                catch (NumberFormatException e) { return argOr(args, kwargs, 0, "default", NumberValue.ofLong(0)); }
            }
            case "float": {
                if (target instanceof NumberValue) return NumberValue.ofDouble(((NumberValue) target).doubleValue());
                try { return NumberValue.ofDouble(Double.parseDouble(target.asText().trim())); }
                catch (NumberFormatException e) { return argOr(args, kwargs, 0, "default", NumberValue.ofDouble(0)); }
            }
            case "abs": {
                NumberValue n = runtime.asNumber(target, pos);
                return n.isInteger() ? NumberValue.ofLong(Math.abs(n.longValue())) : NumberValue.ofDouble(Math.abs(n.doubleValue()));
            }
            case "join": {
                String sep = argOr(args, kwargs, 0, "d", new StringValue("")).asText();
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Value v : runtime.iterateValue(target, pos)) { if (!first) sb.append(sep); first = false; sb.append(v.asText()); }
                return new StringValue(sb.toString());
            }
            case "first": { List<Value> items = runtime.iterateValue(target, pos); return items.isEmpty() ? NoneValue.NONE : items.get(0); }
            case "last": { List<Value> items = runtime.iterateValue(target, pos); return items.isEmpty() ? NoneValue.NONE : items.get(items.size() - 1); }
            case "random": { List<Value> items = runtime.iterateValue(target, pos); return items.isEmpty() ? NoneValue.NONE : items.get(0); }
            case "list": return new ListValue(runtime.iterateValue(target, pos));
            case "reverse": {
                List<Value> items = new ArrayList<>(runtime.iterateValue(target, pos));
                java.util.Collections.reverse(items);
                return new ListValue(items);
            }
            case "sort": {
                List<Value> items = new ArrayList<>(runtime.iterateValue(target, pos));
                String attribute = kwargs.containsKey("attribute") ? kwargs.get("attribute").asText() : null;
                items.sort((a, b) -> runtime.compare(attribute == null ? a : attr(a, attribute), attribute == null ? b : attr(b, attribute), pos));
                if (argOr(args, kwargs, 0, "reverse", BooleanValue.FALSE).isTruthy()) java.util.Collections.reverse(items);
                return new ListValue(items);
            }
            case "unique": {
                List<Value> out = new ArrayList<>();
                for (Value v : runtime.iterateValue(target, pos)) {
                    boolean seen = false;
                    for (Value x : out) if (x.equalsValue(v)) seen = true;
                    if (!seen) out.add(v);
                }
                return new ListValue(out);
            }
            case "min": case "max": {
                List<Value> items = runtime.iterateValue(target, pos);
                if (items.isEmpty()) return NoneValue.NONE;
                Value best = items.get(0);
                for (Value v : items) {
                    int c = runtime.compare(v, best, pos);
                    if (name.equals("min") ? c < 0 : c > 0) best = v;
                }
                return best;
            }
            case "sum": {
                double total = numberArg(argOr(args, kwargs, 1, "start", NumberValue.ofLong(0)), pos);
                boolean allInt = true;
                String attribute = argOr(args, kwargs, 0, "attribute", NoneValue.NONE).isNone() ? null : argOr(args, kwargs, 0, "attribute", NoneValue.NONE).asText();
                for (Value v : runtime.iterateValue(target, pos)) {
                    NumberValue n = runtime.asNumber(attribute == null ? v : attr(v, attribute), pos);
                    total += n.doubleValue();
                    allInt &= n.isInteger();
                }
                return allInt ? NumberValue.ofLong((long) total) : NumberValue.ofDouble(total);
            }
            case "items": {
                List<Value> out = new ArrayList<>();
                if (target instanceof DictValue) {
                    for (Map.Entry<String, Value> e : ((DictValue) target).entries().entrySet()) {
                        List<Value> pair = new ArrayList<>();
                        pair.add(new StringValue(e.getKey()));
                        pair.add(e.getValue());
                        out.add(new ListValue(pair));
                    }
                }
                return new ListValue(out);
            }
            case "dictsort": {
                List<Value> pairs = ((ListValue) apply("items", target, List.of(), Map.of(), pos)).items();
                pairs.sort((a, b) -> runtime.compare(((ListValue) a).items().get(0), ((ListValue) b).items().get(0), pos));
                return new ListValue(pairs);
            }
            case "format": return new StringValue(printf(target.asText(), args, pos));
            case "replace": {
                String old = args.get(0).asText();
                String neu = args.get(1).asText();
                return new StringValue(target.asText().replace(old, neu));
            }
            case "center": {
                int width = (int) numberArg(argOr(args, kwargs, 0, "width", NumberValue.ofLong(80)), pos);
                String s = target.asText();
                int pad = Math.max(0, width - s.length());
                return new StringValue(" ".repeat(pad / 2) + s + " ".repeat(pad - pad / 2));
            }
            case "indent": {
                int width = (int) numberArg(argOr(args, kwargs, 0, "width", NumberValue.ofLong(4)), pos);
                boolean first = argOr(args, kwargs, 1, "first", BooleanValue.FALSE).isTruthy();
                String[] lines = target.asText().split("\n", -1);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) sb.append('\n');
                    if (i > 0 || first) sb.append(" ".repeat(width));
                    sb.append(lines[i]);
                }
                return new StringValue(sb.toString());
            }
            case "tojson": return new StringValue(new com.google.gson.Gson().toJson(target.toJava()), true);
            case "filesizeformat": {
                double bytes = runtime.asNumber(target, pos).doubleValue();
                String[] units = {"Bytes", "kB", "MB", "GB", "TB"};
                int u = 0;
                while (bytes >= 1000 && u < units.length - 1) { bytes /= 1000; u++; }
                return new StringValue(u == 0 ? (long) bytes + " Bytes" : String.format(Locale.ROOT, "%.1f %s", bytes, units[u]));
            }
            case "batch": {
                int size = (int) numberArg(args.get(0), pos);
                List<Value> out = new ArrayList<>();
                List<Value> current = new ArrayList<>();
                for (Value v : runtime.iterateValue(target, pos)) {
                    current.add(v);
                    if (current.size() == size) { out.add(new ListValue(current)); current = new ArrayList<>(); }
                }
                if (!current.isEmpty()) out.add(new ListValue(current));
                return new ListValue(out);
            }
            case "slice": {
                int slices = (int) numberArg(args.get(0), pos);
                List<Value> items = runtime.iterateValue(target, pos);
                List<Value> out = new ArrayList<>();
                int per = (int) Math.ceil(items.size() / (double) Math.max(1, slices));
                for (int i = 0; i < items.size(); i += per) out.add(new ListValue(items.subList(i, Math.min(items.size(), i + per))));
                return new ListValue(out);
            }
            case "attr": return attr(target, args.get(0).asText());
            case "map": {
                List<Value> out = new ArrayList<>();
                String attribute = kwargs.containsKey("attribute") ? kwargs.get("attribute").asText() : null;
                String filter = args.isEmpty() ? null : args.get(0).asText();
                for (Value v : runtime.iterateValue(target, pos)) {
                    out.add(attribute != null ? attr(v, attribute) : apply(filter, v, args.subList(1, args.size()), Map.of(), pos));
                }
                return new ListValue(out);
            }
            case "select": case "reject": case "selectattr": case "rejectattr": {
                boolean byAttr = name.endsWith("attr");
                boolean keep = name.startsWith("select");
                List<Value> out = new ArrayList<>();
                for (Value v : runtime.iterateValue(target, pos)) {
                    Value subject = byAttr ? attr(v, args.get(0).asText()) : v;
                    boolean truthy;
                    int testIndex = byAttr ? 1 : 0;
                    if (args.size() > testIndex) {
                        String test = args.get(testIndex).asText();
                        Value other = args.size() > testIndex + 1 ? args.get(testIndex + 1) : NoneValue.NONE;
                        truthy = test(test, subject, other, pos);
                    } else {
                        truthy = subject.isTruthy();
                    }
                    if (truthy == keep) out.add(v);
                }
                return new ListValue(out);
            }
            case "groupby": {
                String attribute = args.get(0).asText();
                List<Value> out = new ArrayList<>();
                for (Value v : runtime.iterateValue(target, pos)) {
                    Value key = attr(v, attribute);
                    ListValue group = null;
                    for (Value g : out) if (((DictValue) g).get("grouper").equalsValue(key)) group = (ListValue) ((DictValue) g).get("list");
                    if (group == null) {
                        DictValue g = new DictValue();
                        g.put("grouper", key);
                        group = new ListValue(new ArrayList<>());
                        g.put("list", group);
                        out.add(g);
                    }
                    group.items().add(v);
                }
                return new ListValue(out);
            }
            case "xmlattr": {
                StringBuilder sb = new StringBuilder();
                if (target instanceof DictValue) {
                    for (Map.Entry<String, Value> e : ((DictValue) target).entries().entrySet()) {
                        if (e.getValue().isNone()) continue;
                        sb.append(' ').append(e.getKey()).append("=\"").append(HtmlEscaper.escape(e.getValue().asText())).append('"');
                    }
                }
                return new StringValue(sb.toString(), true);
            }
            case "pprint": return new StringValue(target.repr());
            default:
                throw new GenerationException("GEN003", "UnknownFilter", "Filter '|" + name + "' is not implemented", pos);
        }
    }

    /** Evaluates a Jinja test ("defined", "even", "equalto", ...) on a value. */
    public boolean test(String name, Value subject, Value other, SourcePosition pos) {
        switch (name) {
            case "defined": return true;
            case "undefined": return false;
            case "none": return subject.isNone();
            case "number": return subject instanceof NumberValue;
            case "integer": return subject instanceof NumberValue && ((NumberValue) subject).isInteger();
            case "float": return subject instanceof NumberValue && !((NumberValue) subject).isInteger();
            case "string": return subject instanceof StringValue;
            case "boolean": return subject instanceof BooleanValue;
            case "true": return subject instanceof BooleanValue && subject.isTruthy();
            case "false": return subject instanceof BooleanValue && !subject.isTruthy();
            case "iterable": case "sequence": return subject instanceof ListValue || subject instanceof DictValue || subject instanceof StringValue;
            case "mapping": return subject instanceof DictValue;
            case "even": return runtime.asNumber(subject, pos).longValue() % 2 == 0;
            case "odd": return runtime.asNumber(subject, pos).longValue() % 2 != 0;
            case "divisibleby": return runtime.asNumber(subject, pos).longValue() % runtime.asNumber(other, pos).longValue() == 0;
            case "equalto": case "eq": case "==": return subject.equalsValue(other);
            case "ne": case "!=": return !subject.equalsValue(other);
            case "lt": case "<": return runtime.compare(subject, other, pos) < 0;
            case "le": case "<=": return runtime.compare(subject, other, pos) <= 0;
            case "gt": case ">": return runtime.compare(subject, other, pos) > 0;
            case "ge": case ">=": return runtime.compare(subject, other, pos) >= 0;
            case "sameas": return subject == other;
            case "lower": return subject.asText().equals(subject.asText().toLowerCase());
            case "upper": return subject.asText().equals(subject.asText().toUpperCase());
            case "callable": return subject instanceof fjc.evaluate.FunctionValue || subject instanceof fjc.evaluate.BuiltinFunction;
            case "in": return runtime.containsValue(other, subject, pos);
            case "escaped": return subject instanceof StringValue && ((StringValue) subject).isSafe();
            default:
                throw new GenerationException("GEN004", "UnknownTest", "Test 'is " + name + "' is not implemented", pos);
        }
    }

    // ------------------------------------------------------------- helpers

    private long length(Value v, SourcePosition pos) {
        if (v instanceof ListValue) return ((ListValue) v).size();
        if (v instanceof DictValue) return ((DictValue) v).entries().size();
        if (v instanceof StringValue) return ((StringValue) v).value().length();
        throw new GenerationException("GEN005", "TypeError", "object of type '" + v.typeName() + "' has no length", pos);
    }

    private static Value attr(Value v, String name) {
        if (v instanceof DictValue) { Value x = ((DictValue) v).get(name); return x == null ? NoneValue.NONE : x; }
        if (v instanceof fjc.evaluate.ObjectValue) { Value x = ((fjc.evaluate.ObjectValue) v).get(name); return x == null ? NoneValue.NONE : x; }
        return NoneValue.NONE;
    }

    private static Value argOr(List<Value> args, Map<String, Value> kwargs, int index, String keyword, Value fallback) {
        if (index < args.size()) return args.get(index);
        if (kwargs.containsKey(keyword)) return kwargs.get(keyword);
        return fallback;
    }

    private double numberArg(Value v, SourcePosition pos) {
        return runtime.asNumber(v, pos).doubleValue();
    }

    private String truncate(String s, List<Value> args, Map<String, Value> kwargs, SourcePosition pos) {
        int length = (int) numberArg(argOr(args, kwargs, 0, "length", NumberValue.ofLong(255)), pos);
        boolean killwords = argOr(args, kwargs, 1, "killwords", BooleanValue.FALSE).isTruthy();
        String end = argOr(args, kwargs, 2, "end", new StringValue("...")).asText();
        int leeway = (int) numberArg(argOr(args, kwargs, 3, "leeway", NumberValue.ofLong(5)), pos);
        if (s.length() <= length + leeway) return s;
        int cut = Math.max(0, length - end.length());
        String head = s.substring(0, cut);
        if (!killwords) {
            int space = head.lastIndexOf(' ');
            if (space > 0) head = head.substring(0, space);
        }
        return head + end;
    }

    private static String title(String s) {
        StringBuilder sb = new StringBuilder();
        boolean start = true;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) { sb.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c)); start = false; }
            else { sb.append(c); start = true; }
        }
        return sb.toString();
    }

    private static final Pattern PRINTF = Pattern.compile("%([-+ 0#]*)(\\d+)?(?:\\.(\\d+))?([sdifxX%])");

    /** Python printf-style formatting: "%.2f"|format(300) -> "300.00". */
    String printf(String format, List<Value> args, SourcePosition pos) {
        Matcher m = PRINTF.matcher(format);
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int last = 0;
        while (m.find()) {
            sb.append(format, last, m.start());
            last = m.end();
            String flags = m.group(1);
            String width = m.group(2);
            String precision = m.group(3);
            char conv = m.group(4).charAt(0);
            if (conv == '%') { sb.append('%'); continue; }
            if (argIndex >= args.size()) {
                throw new GenerationException("GEN006", "FormatError", "not enough arguments for format string '" + format + "'", pos);
            }
            Value v = args.get(argIndex++);
            String javaFlags = flags.replace("#", "");
            String spec;
            switch (conv) {
                case 's': spec = "%" + javaFlags + (width == null ? "" : width) + (precision == null ? "" : "." + precision) + "s";
                    sb.append(String.format(Locale.ROOT, spec, v.asText())); break;
                case 'd': case 'i': spec = "%" + javaFlags + (width == null ? "" : width) + "d";
                    sb.append(String.format(Locale.ROOT, spec, runtime.asNumber(v, pos).longValue())); break;
                case 'f': spec = "%" + javaFlags + (width == null ? "" : width) + "." + (precision == null ? "6" : precision) + "f";
                    sb.append(String.format(Locale.ROOT, spec, runtime.asNumber(v, pos).doubleValue())); break;
                case 'x': case 'X': spec = "%" + javaFlags + (width == null ? "" : width) + conv;
                    sb.append(String.format(Locale.ROOT, spec, runtime.asNumber(v, pos).longValue())); break;
                default: sb.append(m.group());
            }
        }
        sb.append(format.substring(last));
        if (argIndex < args.size()) {
            throw new GenerationException("GEN006", "FormatError", "not all arguments converted during formatting of '" + format + "'", pos);
        }
        return sb.toString();
    }
}
