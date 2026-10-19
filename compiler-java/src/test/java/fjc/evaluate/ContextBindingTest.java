package fjc.evaluate;

import fjc.TestPaths;
import fjc.pipeline.CompilationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Python data → evaluator → context bound to the Jinja templates. */
class ContextBindingTest {

    @TempDir static Path temp;
    static CompilationResult result;

    @BeforeAll
    static void compile() {
        result = TestPaths.compile(TestPaths.sampleProject(), temp, false);
        assertTrue(result.success(), result.errors().all().toString());
    }

    @Test
    void moduleDataIsEvaluatedFromThePythonAst() {
        ContextData data = result.contexts();
        ListValue products = (ListValue) data.moduleData().get("products");
        assertEquals(5, products.size());
        DictValue first = (DictValue) products.items().get(0);
        assertEquals("Wireless Headphones Pro", first.get("name").asText());
        assertEquals(129.99, ((NumberValue) first.get("price")).doubleValue(), 0.0001);
        assertEquals(1L, ((NumberValue) first.get("id")).longValue());
    }

    @Test
    void routesWithoutParametersGiveOnePageContext() {
        List<PageContext> index = result.contexts().pagesFor("index.jinja");
        assertEquals(1, index.size());
        PageContext page = index.get(0);
        assertEquals("index", page.endpoint());
        assertEquals(List.of("products", "categories").toString(), page.context().keySet().toString());
        assertEquals(5, ((ListValue) page.context().get("products")).size());
        assertEquals(4, ((ListValue) page.context().get("categories")).size());
    }

    @Test
    void parameterizedRoutesAreInstantiatedPerProductThroughTheRouteBody() {
        List<PageContext> details = result.contexts().pagesFor("product_detail.jinja");
        assertEquals(5, details.size(), "find_product() was executed once per id");
        PageContext second = details.get(1);
        assertEquals("product_id=2", second.queryString());
        assertEquals("/product/2", second.runtimeUrl());
        DictValue product = (DictValue) second.context().get("product");
        assertEquals("Gaming Mechanical Keyboard", product.get("name").asText());
        List<PageContext> edits = result.contexts().pagesFor("edit_product.jinja");
        assertEquals(5, edits.size());
        assertTrue(edits.get(0).context().containsKey("categories"));
    }

    @Test
    void contextDataIsWrittenAsJson() throws Exception {
        String json = Files.readString(result.compilerOutputFiles().get("context_data.json"));
        assertTrue(json.contains("\"moduleData\""));
        assertTrue(json.contains("\"runtimeUrl\": \"/product/3\""));
        assertTrue(json.contains("Smart TV 55"));
    }
}
