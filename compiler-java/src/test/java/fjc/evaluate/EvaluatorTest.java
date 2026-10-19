package fjc.evaluate;

import fjc.python.ast.FunctionDefinition;
import fjc.python.ast.PythonAstBuilder;
import fjc.python.ast.PythonProgram;
import fjc.python.lexer.PythonLexer;
import fjc.python.parser.PythonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The compile-time interpreter for the Python subset. */
class EvaluatorTest {

    static Evaluator run(String src) {
        PythonProgram program = new PythonAstBuilder("app.py").build(new PythonParser(new PythonLexer("app.py", src).tokenize()).parseModule());
        Evaluator ev = new Evaluator(null);
        ev.runModule(program);
        return ev;
    }

    @Test
    void evaluatesDataStructuresArithmeticAndStrings() {
        Evaluator ev = run("items = [{\"id\": 1, \"price\": 2.5}, {\"id\": 2, \"price\": 4}]\n"
                + "total = items[0][\"price\"] + items[1][\"price\"] * 2\n"
                + "name = \"ab\".upper() + str(len(items)) + \"-\" + str(7 // 2) + \"-\" + str(7 % 3)\n"
                + "flag = 1 < 2 and not (3 == 4) or False\n");
        assertEquals(10.5, ((NumberValue) ev.globals().get("total")).doubleValue(), 1e-9);
        assertEquals("AB2-3-1", ev.globals().get("name").asText());
        assertTrue(ev.globals().get("flag").isTruthy());
        assertEquals("[{'id': 1, 'price': 2.5}, {'id': 2, 'price': 4}]", ev.globals().get("items").asText());
    }

    @Test
    void executesFunctionsLoopsConditionalsAndMutation() {
        Evaluator ev = run("xs = [3, 1, 2]\n"
                + "def biggest(values):\n    best = 0\n    for v in values:\n        if v > best:\n            best = v\n    return best\n"
                + "def label(n):\n    if n > 2:\n        return \"big\"\n    elif n > 1:\n        return \"mid\"\n    else:\n        return \"small\"\n"
                + "m = biggest(xs)\nxs.append(10)\nxs.remove(1)\nl = label(m)\ncount = len(xs)\nsorted_xs = sorted(xs)\n");
        assertEquals(3L, ((NumberValue) ev.globals().get("m")).longValue());
        assertEquals("big", ev.globals().get("l").asText());
        assertEquals(3L, ((NumberValue) ev.globals().get("count")).longValue());
        assertEquals("[2, 3, 10]", ev.globals().get("sorted_xs").asText());
    }

    @Test
    void routeEvaluationFollowsTheGetPathAndRecordsRenderTemplateCalls() {
        Evaluator ev = run("from flask import Flask, render_template, request, redirect, url_for, abort\n"
                + "app = Flask(__name__)\napp.secret_key = \"k\"\n"
                + "products = [{\"id\": 1, \"name\": \"A\"}, {\"id\": 2, \"name\": \"B\"}]\n"
                + "def find(pid):\n    for p in products:\n        if p[\"id\"] == pid:\n            return p\n    return None\n"
                + "@app.route(\"/edit/<int:pid>\", methods=[\"GET\", \"POST\"])\ndef edit(pid):\n    product = find(pid)\n"
                + "    if product is None:\n        abort(404)\n    if request.method == \"POST\":\n        products.remove(product)\n        return redirect(url_for(\"edit\", pid=pid))\n"
                + "    return render_template(\"edit.jinja\", product=product, all=products)\n");
        FunctionValue edit = (FunctionValue) ev.globals().get("edit");
        Map<String, Value> args = new LinkedHashMap<>();
        args.put("pid", NumberValue.ofLong(2));
        List<RenderInvocation> renders = ev.callRoute(edit, args);
        assertEquals(1, renders.size());
        assertEquals("edit.jinja", renders.get(0).templateName());
        assertEquals("B", ((DictValue) renders.get(0).context().get("product")).get("name").asText());
        assertEquals(2, ((ListValue) renders.get(0).context().get("all")).size(), "POST branch was not executed");
        args.put("pid", NumberValue.ofLong(9));
        Evaluator.AbortSignal abort = assertThrows(Evaluator.AbortSignal.class, () -> ev.callRoute(edit, args));
        assertEquals(404, abort.code());
        assertEquals("k", ((ObjectValue) ev.globals().get("app")).get("secret_key").asText());
    }

    @Test
    void runtimeFailuresAreReportedAsGenerationErrorsWithPositions() {
        EvaluationException e = assertThrows(EvaluationException.class, () -> run("d = {\"a\": 1}\nx = d[\"b\"]\n"));
        assertEquals("EVAL003", e.error().code());
        assertEquals(2, e.error().line());
        EvaluationException z = assertThrows(EvaluationException.class, () -> run("x = 1 / 0\n"));
        assertEquals("ZeroDivisionError", z.error().name());
        FunctionDefinition unused = null;
        assertEquals(null, unused);
    }

    @Test
    void numberFormattingMatchesPython() {
        assertEquals("300.0", NumberValue.ofDouble(300).asText());
        assertEquals("129.99", NumberValue.ofDouble(129.99).asText());
        assertEquals("7", NumberValue.ofLong(7).asText());
        assertEquals("True", BooleanValue.TRUE.asText());
        assertEquals("None", NoneValue.NONE.asText());
    }
}
