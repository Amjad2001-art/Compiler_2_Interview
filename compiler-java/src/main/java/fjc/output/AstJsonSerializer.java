package fjc.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fjc.ast.AstNode;

import java.util.List;
import java.util.Map;

/**
 * Serializes an AST to structured JSON: every node becomes an object with
 * {@code type}, {@code line}, {@code column}, {@code file}, its semantic
 * properties and a {@code children} array. Grammar punctuation never appears
 * because it is not part of the AST.
 */
public final class AstJsonSerializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

    // تُحوَّل العقدة وخصائصها وأبناؤها إلى تمثيل قابل للحفظ دون تغيير بنية الشجرة.
    public JsonObject toJson(AstNode node) {
        JsonObject o = new JsonObject();
        o.addProperty("type", node.nodeName());
        o.addProperty("line", node.line());
        o.addProperty("column", node.column());
        o.addProperty("file", node.fileName());
        for (Map.Entry<String, Object> e : node.properties().entrySet()) {
            o.add(e.getKey(), toElement(e.getValue()));
        }
        JsonArray children = new JsonArray();
        for (AstNode c : node.children()) children.add(toJson(c));
        o.add("children", children);
        return o;
    }

    public String toPrettyJson(AstNode node) {
        return GSON.toJson(toJson(node));
    }

    static JsonElement toElement(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof Boolean) return new JsonPrimitive((Boolean) v);
        if (v instanceof Number) return new JsonPrimitive((Number) v);
        if (v instanceof String) return new JsonPrimitive((String) v);
        if (v instanceof List) {
            JsonArray a = new JsonArray();
            for (Object x : (List<?>) v) a.add(toElement(x));
            return a;
        }
        if (v instanceof Map) {
            JsonObject o = new JsonObject();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) o.add(String.valueOf(e.getKey()), toElement(e.getValue()));
            return o;
        }
        return new JsonPrimitive(String.valueOf(v));
    }
}
