package fjc.semantic;

import fjc.TestPaths;
import fjc.pipeline.CompilationResult;
import fjc.symbols.Scope;
import fjc.symbols.Symbol;
import fjc.symbols.SymbolKind;
import fjc.symbols.SymbolTable;
import fjc.symbols.SymbolTablePrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolTableTest {

    @TempDir Path temp;

    @Test
    void scopesResolveThroughParentsAndPrintAsTree() {
        SymbolTable table = new SymbolTable();
        table.define("products", SymbolKind.MODULE_VARIABLE, ValueShape.list(ValueShape.openDict()), null, "");
        Scope fn = table.enter("index", "function");
        table.define("product", SymbolKind.LOCAL_VARIABLE, ValueShape.openDict(), null, "");
        assertNotNull(table.lookup("products"), "outer symbols are visible");
        assertEquals(fn, table.current().owner("product"));
        table.exit();
        assertNull(table.lookup("product"), "inner symbols are not visible outside their scope");
        String printed = new SymbolTablePrinter(true).print(table);
        assertTrue(printed.contains("[scope global] global"));
        assertTrue(printed.contains("[scope function] index"));
        assertTrue(printed.contains("products : module variable [list[dict]]"));
    }

    @Test
    void sampleProjectSymbolTableContainsRoutesTemplatesAndShapes() throws Exception {
        CompilationResult r = TestPaths.compile(TestPaths.sampleProject(), temp, true);
        assertTrue(r.success(), r.errors().all().toString());
        SymbolTable t = r.symbols();
        Symbol products = t.findScope("app.py").lookupLocal("products");
        assertEquals(SymbolKind.MODULE_VARIABLE, products.kind());
        assertEquals("list[dict[id, name, price, category, details, image]]", products.shape().describe());
        assertEquals(7, t.symbolsOfKind(SymbolKind.ROUTE).size());
        Scope template = t.findScope("index.jinja");
        assertNotNull(template, "each template gets a scope");
        assertEquals(SymbolKind.CONTEXT_VARIABLE, template.lookupLocal("products").kind());
        assertTrue(template.lookupLocal("products").detail().contains("index()"));
        Scope loop = template.children().stream().filter(s -> s.name().equals("for product")).findFirst().orElseThrow();
        assertEquals(SymbolKind.LOOP_VARIABLE, loop.lookupLocal("product").kind());
        String file = Files.readString(r.compilerOutputFiles().get("symbol_table.txt"));
        assertTrue(file.startsWith("SYMBOL TABLE"));
        assertTrue(file.contains("[scope template] index.jinja"));
    }
}
