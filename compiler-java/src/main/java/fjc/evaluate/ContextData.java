package fjc.evaluate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The prepared context data: every page instance to generate plus the
 * module-level data of the Python program. This (together with the validated
 * ASTs) is the only input of the code generator.
 */
public final class ContextData {
    private final List<PageContext> pages = new ArrayList<>();
    private final Map<String, Value> moduleData = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    public void addPage(PageContext page) { pages.add(page); }
    public void putModuleData(String name, Value value) { moduleData.put(name, value); }
    public void note(String message) { notes.add(message); }

    public List<PageContext> pages() { return Collections.unmodifiableList(pages); }
    public Map<String, Value> moduleData() { return Collections.unmodifiableMap(moduleData); }
    public List<String> notes() { return Collections.unmodifiableList(notes); }

    public List<PageContext> pagesFor(String templateName) {
        List<PageContext> out = new ArrayList<>();
        for (PageContext p : pages) if (p.templateName().equals(templateName)) out.add(p);
        return out;
    }

    public List<String> templateNames() {
        List<String> out = new ArrayList<>();
        for (PageContext p : pages) if (!out.contains(p.templateName())) out.add(p.templateName());
        return out;
    }
}
