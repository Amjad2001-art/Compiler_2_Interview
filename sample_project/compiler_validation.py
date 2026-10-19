"""واجهة تشغيل المترجم من المتصفح.

هذه الطبقة واجهة عرض وتشغيل فقط؛ لا تعيد كتابة قواعد المترجم ولا تُحلّل
اللغات بنفسها. مصدر الحقيقة هو ملف Java JAR وتقاريره الرسمية.
"""
from __future__ import annotations

import re
import secrets
import subprocess
import threading
from pathlib import Path
from flask import abort, render_template_string, request
from regenerate import COMPILER_OUTPUT_DIR, JAR, OUTPUT_DIR, ROOT
from semantic_fix_demo import (RECIPES, active_workspace, applied_fixes,
                               change_fix, reset_workspace, workspace_paths)

# مهم: اختيار المشروع يتم من قائمة ثابتة؛ لا نقبل مسارات أو أوامر من المستخدم.
PROJECTS = {
    "valid": ("sample_project", "Valid project"),
    "invalid": ("sample_project_invalid", "Invalid project"),
}
ROOT_PATH = Path(ROOT).resolve()
JAR_PATH = Path(JAR).resolve()
RUN_LOCK = threading.Lock()
TIMEOUT_SECONDS = 120
# رمز لحماية أزرار تغيير ملفات نسخة العرض من الطلبات غير الصادرة من الصفحة.
DEMO_FORM_TOKEN = secrets.token_urlsafe(32)

# التقارير الرسمية الأربعة لا نخلطها مع مخرجات الاختبارات المخصصة.
OFFICIAL_FILES = ["ast_python.json", "ast_jinja.json", "semantic_report.txt", "generation_log.txt"]

# الأخطاء والأحجام تُقرأ من تقارير Java الحقيقية؛ لا نبني نتائج وهمية.
ERROR_RE = re.compile(
    r"^\s*\[([A-Z]{2}\d{3})\]\s+ERROR\s+(.+?):(\d+):(\d+)\s+(\w+):\s+(.*)$"
)
FILE_RE = re.compile(
    r"^\s*(\S+\.html) \(from (.+?), (\d+) instance\(s\), (\d+) bytes\)\s*$"
)


def read_diagnostics(report_path: Path, project_dir: Path):
    """قراءة نتائج الفحص الدلالي الحقيقية دون استنتاج الأخطاء من أسماء الملفات."""
    result = {"python": [], "jinja": [], "other": []}
    if not report_path.is_file():
        return result
    section = "other"
    for line in report_path.read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("== Python/Flask"):
            section = "python"
        elif line.startswith("== Jinja semantic"):
            section = "jinja"
        elif line.startswith("== "):
            section = "other"
        match = ERROR_RE.match(line)
        if not match:
            continue
        code, filename, line_no, col_no, name, explanation = match.groups()
        # تقرير Java يحتفظ باسم الملف وموقعه، ونحوّله لمسار المصدر الكامل للعرض.
        if section == "python":
            full_file = project_dir / filename
        elif section == "jinja":
            full_file = project_dir / "templates" / filename
        else:
            full_file = project_dir / filename
        item = {
            "code": code,
            "file": str(full_file.resolve()) if full_file.is_file() else filename,
            "line": int(line_no),
            "column": int(col_no),
            "name": name,
            "explanation": explanation,
        }
        result[section].append(item)
    return result


def parsed_console(text: str):
    """تحويل ملخص تشغيل Java إلى بيانات مناسبة لبطاقات الواجهة."""
    stage = re.search(r"^Stage reached:\s*(.+)$", text, re.M)
    python = re.search(r"^Python:\s*(.+)$", text, re.M)
    jinja = re.search(r"^Jinja:\s*(.+)$", text, re.M)
    generated, copied = [], []
    mode = None
    for line in text.splitlines():
        if line.startswith("GENERATED FILES ("):
            mode = "generated"
            continue
        if line.strip() == "COPIED SUPPORT FILES:":
            mode = "copied"
            continue
        if line.startswith("COMPILER OUTPUT (") or line.startswith("AUXILIARY REPORTS ("):
            mode = None
        if mode == "generated":
            item = FILE_RE.match(line)
            if item:
                name, source, instances, count = item.groups()
                generated.append({"name": name, "source": source, "instances": int(instances), "bytes": int(count)})
        elif mode == "copied" and line.startswith("  "):
            copied.append(line.strip())
    return {
        "stage": stage.group(1).strip() if stage else "Unknown",
        "python_summary": python.group(1).strip() if python else None,
        "jinja_summary": jinja.group(1).strip() if jinja else None,
        "generated": generated,
        "copied": copied,
    }


def console_output_for_display(raw: str, status: str) -> str:
    """إخفاء قائمة ملفات التقارير من العرض عند الفشل فقط، دون تعديل سجل Java الأصلي أو الملفات."""
    if status != "semantic_failure":
        return raw
    result = []
    suppress_artifacts = False
    for line in raw.splitlines(keepends=True):
        if line.startswith("COMPILER OUTPUT (") or line.startswith("AUXILIARY REPORTS ("):
            suppress_artifacts = True
            continue
        if suppress_artifacts:
            if line.startswith("RESULT:"):
                suppress_artifacts = False
            else:
                continue
        result.append(line)
    return "".join(result)


def unavailable_result(selected: str, message: str):
    """توحيد بيانات العرض عندما يتعذر تشغيل Java بدل إظهار حقول ناقصة."""
    return {"status": "unavailable", "selected": selected, "message": message,
            "stage": "Not completed", "errors_count": 0,
            "diagnostics": {"python": [], "jinja": [], "other": []},
            "python_summary": None, "jinja_summary": None,
            "generated": [], "copied": [], "reports": [],
            "reports_dir": "", "auxiliary_dir": "", "raw": ""}


# هذه الواجهة تختار أحد مشروعي الاختبار فقط وتشغّل مترجم جافا الأصلي؛ لا تعيد تنفيذ التحليل دلاليا.
def compile_selected(selected: str):
    """تشغيل Java على الملفات الفعلية مع عزل التقارير عند اختبار الأخطاء."""
    if selected not in PROJECTS:
        raise ValueError("Unknown project selection")
    project_folder, _ = PROJECTS[selected]
    project_dir = ROOT_PATH / project_folder
    if selected == "invalid":
        # الإصلاحات تُطبّق مباشرة على sample_project_invalid؛ الاستعادة متاحة دائمًا.
        project_dir = active_workspace(ROOT_PATH, project_dir)
    if selected == "valid":
        # المشروع الصحيح يستخدم وجهات المترجم الاعتيادية: output و compiler_output.
        output_dir = Path(OUTPUT_DIR).resolve()
        reports_dir = Path(COMPILER_OUTPUT_DIR).resolve()
    else:
        # فحص النسخة الخاطئة معزول: لا يحذف صفحات المستخدم الصحيحة أو تقارير الأشجار.
        test_base = ROOT_PATH / "compiler_validation_runs" / "invalid"
        output_dir = test_base / "output"
        reports_dir = test_base / "compiler_output"
    if not JAR_PATH.is_file() or not project_dir.is_dir():
        return unavailable_result(selected, "ملف JAR أو مجلد المشروع غير موجود. تأكد من بناء المترجم أولًا.")

    cmd = ["java", "-jar", str(JAR_PATH), str(project_dir),
           "--output", str(output_dir), "--compiler-output", str(reports_dir),
           "--no-color", "--ascii"]
    try:
        # عرض الطرفية فقط يعكس التشغيل الحقيقي؛ لا نعيد تنفيذ Lexer أو Parser في Flask.
        completed = subprocess.run(cmd, cwd=str(ROOT_PATH), capture_output=True,
                                   text=True, encoding="utf-8", errors="replace",
                                   timeout=TIMEOUT_SECONDS, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return unavailable_result(selected, "تعذر تنفيذ Java أو انتهت المهلة: " + str(exc))

    raw = (completed.stdout or "") + ("\n" + completed.stderr if completed.stderr else "")
    summary = parsed_console(raw)
    diagnostics = read_diagnostics(reports_dir / "semantic_report.txt", project_dir)
    errors_count = sum(map(len, diagnostics.values()))
    semantic_blocked = "CODE GENERATION ABORTED: semantic errors" in raw and errors_count > 0
    # لا نظهر زر الإصلاح إلا للخطأ الذي يطابق اسمه ورمزه ومقطع مصدره المحدد.
    if selected == "invalid":
        for group in diagnostics.values():
            for item in group:
                recipe = RECIPES.get(item["code"])
                if recipe and item["name"] == recipe.name and Path(item["file"]).name == Path(recipe.relative_file).name:
                    source_path = project_dir / recipe.relative_file
                    if source_path.is_file() and source_path.read_text(encoding="utf-8").count(recipe.broken) == 1:
                        item["fix_key"] = recipe.code
                        item["fix_description"] = recipe.description
    if completed.returncode == 0 and "RESULT: SUCCESS" in raw:
        status = "success"
    elif semantic_blocked and completed.returncode == 1:
        status = "semantic_failure"
    else:
        status = "unavailable"
    return {
        "selected": selected,
        "status": status,
        "message": "" if status != "unavailable" else "المترجم لم يُنهِ التنفيذ بالشكل المتوقع؛ راجع سجل التشغيل أدناه.",
        "exit_code": completed.returncode,
        "errors_count": errors_count,
        "diagnostics": diagnostics,
        "output_dir": str(output_dir),
        "reports_dir": str(reports_dir),
        "reports": [name for name in OFFICIAL_FILES if (reports_dir / name).is_file()],
        "auxiliary_dir": str(reports_dir.with_name("compiler_details")),
        "raw": raw[-50000:],
        "working_copy": str(project_dir) if selected == "invalid" else "",
        "applied_fixes": applied_fixes(ROOT_PATH) if selected == "invalid" else [],
        # خاص بالواجهة فقط؛ يبقى النص الأصلي كما هو داخل raw.
        "display_raw": console_output_for_display(raw[-50000:], status),
        **summary,
    }


PAGE = r"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>Compiler Validation | ProductShop</title>
  <link rel="stylesheet" href="/compiler/assets/validation.css">
  <script defer src="/compiler/assets/validation.js"></script>
</head>
<body>
  <header class="topbar">
    <div class="topbar-inner">
      <a class="brand" href="/compiler/validate"><span class="logo">{ }</span><span>Compiler Validation<small>FLASK · JINJA · JAVA COMPILER</small></span></a>
      <nav aria-label="Navigation"><a href="/">Original site</a><a href="/output/index.html">Generated site</a><a href="/compiler/trees">Compiler Trees</a></nav>
    </div>
  </header>
  <main class="container">
    <section class="hero">
      <div class="eyebrow"><span class="live-dot"></span> LIVE COMPILER EXECUTION</div>
      <h1>Semantic Analysis <span>&amp; Compiler Results</span></h1>
      <p>Run your real Java compiler on a valid or intentionally invalid Flask/Jinja project. Explore the stages, semantic diagnostics, and generated artifacts without leaving the browser.</p>
      <div class="hero-flow"><span>Lexer</span><b>→</b><span>Parser</span><b>→</b><span>AST</span><b>→</b><span>Semantic</span><b>→</b><span>Context</span><b>→</b><span>Code Generation</span></div>
    </section>

    <section class="selector panel">
      <div class="section-heading"><div><p class="section-overline">PROJECT SELECTION</p><h2>Choose a project to compile</h2><p>This runs the Java JAR, not a simulated checker.</p></div><span class="counter">01 / 02</span></div>
      <form method="post" action="/compiler/validate" id="run-form">
        <div class="project-grid">
          <label class="project-card"><input type="radio" name="project" value="valid" {% if selected != 'invalid' %}checked{% endif %}>
            <span class="project-icon good">✓</span><span class="project-title">sample_project</span><span class="project-desc">Valid project · generate connected HTML pages</span><span class="project-tag success">EXPECTED SUCCESS</span>
          </label>
          <label class="project-card"><input type="radio" name="project" value="invalid" {% if selected == 'invalid' %}checked{% endif %}>
            <span class="project-icon bad">!</span><span class="project-title">sample_project_invalid</span><span class="project-desc">Intentionally invalid · inspect real Python &amp; Jinja semantic errors</span><span class="project-tag danger">EXPECTED DIAGNOSTICS</span>
          </label>
        </div>
        <div class="run-area"><span>All compilation stages execute through the same Java compiler as the terminal command.</span><button type="submit" id="run-button">▶ &nbsp; Run Compiler</button></div>
      </form>
    </section>

    {% if result %}
      <section id="results" class="result-section" aria-live="polite">
        {% if action_message %}<div class="action-message panel" role="status">{{ action_message }}</div>{% endif %}
        {% if result.selected == 'invalid' %}
          <div class="demo-note panel"><strong>Interactive repair demonstration</strong><p>Fix and Undo edit the actual <code>sample_project_invalid</code> source files. Each action recompiles the real source. Restore Original returns all eight files to their pristine state with the ten intentional errors.</p><code>{{ result.working_copy }}</code>
            <form method="post" action="/compiler/validate#results" class="reset-form"><input type="hidden" name="action" value="reset"><input type="hidden" name="project" value="invalid"><input type="hidden" name="csrf" value="{{ csrf_token }}"><button type="submit" class="reset-btn" onclick="return confirm('Restore all eight original sample_project_invalid files and their ten intentional errors? Any edits made to this test project will be overwritten.')">↺ Restore Original · All 10 Errors</button></form>
          </div>
        {% endif %}
        <div class="status-banner {% if result.status == 'success' %}ok{% elif result.status == 'semantic_failure' %}not-ok{% else %}system-error{% endif %}">
          <span class="status-icon">{% if result.status == 'success' %}✓{% else %}!{% endif %}</span>
          <div><p class="status-kicker">COMPILATION RESULT</p><h2>{% if result.status == 'success' %}Compilation successful{% elif result.status == 'semantic_failure' %}Semantic validation failed{% else %}Compiler execution unavailable{% endif %}</h2>
          <p>{% if result.status == 'success' %}Semantic analysis passed. No errors found. The compiler generated the requested HTML files.{% elif result.status == 'semantic_failure' %}The compiler detected semantic errors and correctly blocked code generation for this project.{% else %}{{ result.message }}{% endif %}</p></div>
          <span class="status-pill">{% if result.status == 'success' %}SUCCESS{% elif result.status == 'semantic_failure' %}FAILURE · EXPECTED{% else %}EXECUTION ERROR{% endif %}</span>
        </div>
        <div class="metrics">
          <div class="metric"><span>Stage reached</span><strong>{{ result.stage }}</strong></div>
          <div class="metric"><span>Semantic errors</span><strong>{{ result.errors_count }}</strong></div>
          <div class="metric"><span>Python / Flask errors</span><strong>{{ result.diagnostics.python|length }}</strong></div>
          <div class="metric"><span>Jinja errors</span><strong>{{ result.diagnostics.jinja|length }}</strong></div>
        </div>
        {% if result.python_summary or result.jinja_summary %}
        <div class="pipeline-notes panel"><h3>AST Construction Summary</h3>
          {% if result.python_summary %}<div class="summary-line"><span>PYTHON</span><code>{{ result.python_summary }}</code></div>{% endif %}
          {% if result.jinja_summary %}<div class="summary-line"><span>JINJA</span><code>{{ result.jinja_summary }}</code></div>{% endif %}
        </div>
        {% endif %}
        {% if result.selected == 'invalid' and result.applied_fixes %}
          <div class="panel fixes-panel">
            <div class="section-heading"><div><p class="section-overline">REVERSIBLE SOURCE FIXES</p><h2>Fixed issues · Undo anytime</h2><p>These corrections are saved in the actual sample_project_invalid source. Undo restores the erroneous code for this issue and reruns semantic analysis.</p></div><span class="count-badge">{{ result.applied_fixes|length }} fixed</span></div>
            <div class="fixed-list">{% for fix in result.applied_fixes %}
              <div class="fixed-item"><div><span class="fixed-code">{{ fix.code }}</span> <strong>{{ fix.name }}</strong><p dir="rtl" lang="ar">{{ fix.description }}</p><code>{{ fix.relative_file }}</code>
                  <div class="fix-diff"><div><span>BEFORE · ERROR</span><pre>{{ fix.broken }}</pre></div><div><span>AFTER · FIXED</span><pre>{{ fix.corrected }}</pre></div></div>
                </div>
                <form method="post" action="/compiler/validate#results" class="fix-form"><input type="hidden" name="action" value="undo"><input type="hidden" name="project" value="invalid"><input type="hidden" name="key" value="{{ fix.code }}"><input type="hidden" name="csrf" value="{{ csrf_token }}"><button type="submit" class="undo-btn" aria-label="Undo {{ fix.code }}">↶ Undo</button></form>
              </div>
            {% endfor %}</div>

          </div>
        {% endif %}
        {% if result.status == 'success' %}
          <div class="panel results-panel"><div class="section-heading"><div><p class="section-overline">CODE GENERATION</p><h2>Generated HTML files</h2></div><span class="count-badge">{{ result.generated|length }} files</span></div><p class="path-label">{{ result.output_dir }}</p>
          <div class="file-list">{% for file in result.generated %}<div class="file-row"><span class="file-symbol">HTML</span><strong>{{ file.name }}</strong><span>from {{ file.source }}</span><span>{{ file.instances }} instance(s)</span><span>{{ file.bytes }} bytes</span></div>{% endfor %}</div>
          <h3>Copied support files</h3><div class="chips">{% for f in result.copied %}<span class="chip">{{ f }}</span>{% endfor %}</div>
          </div>
        {% elif result.status == 'semantic_failure' %}
          <div class="panel results-panel"><div class="section-heading"><div><p class="section-overline">SEMANTIC DIAGNOSTICS</p><h2>Detected errors</h2><p>Based on the compiler-produced semantic_report.txt, preserving the original code, file, line, column, and explanation.</p></div><span class="count-badge error">{{ result.errors_count }} errors</span></div>
            {% for kind,title in [('python','Python / Flask semantic errors'),('jinja','Jinja semantic errors'),('other','Other semantic errors')] %}
              {% if result.diagnostics[kind] %}<div class="diagnostic-group"><div class="group-heading"><h3>{{ title }}</h3><span>{{ result.diagnostics[kind]|length }} error(s)</span></div>
                {% for err in result.diagnostics[kind] %}<article class="error-card"><div class="error-title"><span class="error-index">#{{ '%02d'|format(loop.index) }}</span><span class="error-code">{{ err.code }}</span><h4>{{ err.name }}</h4><span class="error-badge">ERROR</span></div><div class="error-location"><span class="small-label">FILE</span><code>{{ err.file }}</code><div class="pos"><span>Line {{ err.line }}</span><span>Column {{ err.column }}</span></div></div><div class="error-explanation"><span class="small-label">EXPLANATION</span><p>{{ err.explanation }}</p></div>
                {% if err.fix_key is defined %}<div class="repair-action"><div><strong>Demonstrate the correction</strong><p dir="rtl" lang="ar">{{ err.fix_description }}</p></div>
                  <form method="post" action="/compiler/validate#results" class="fix-form"><input type="hidden" name="action" value="fix"><input type="hidden" name="project" value="invalid"><input type="hidden" name="key" value="{{ err.fix_key }}"><input type="hidden" name="csrf" value="{{ csrf_token }}"><button type="submit" class="fix-btn" aria-label="Fix {{ err.code }}">✓ Fix Error</button></form>
                </div>{% endif %}</article>{% endfor %}
              </div>{% endif %}
            {% endfor %}
            <div class="aborted">✕ &nbsp; CODE GENERATION ABORTED — semantic errors</div>
          </div>
        {% endif %}
        {# دليل الملفات الرسمية يظهر عند نجاح التوليد فقط، ولا يظهر مع الأخطاء الدلالية. #}
        {% if result.status == 'success' %}
          <div class="panel reports-panel"><div class="section-heading"><div><p class="section-overline">COMPILER ARTIFACTS</p><h2>Compiler output</h2></div><span class="count-badge">{{ result.reports|length }} files</span></div><p class="path-label">{{ result.reports_dir }}</p><div class="chips">{% for file in result.reports %}<span class="chip report">{{ file }}</span>{% endfor %}</div><p class="help-text">Compiler output reports refreshed after successful compilation.</p></div>
        {% endif %}
        <details class="console panel"><summary>▸ View Java terminal output</summary><pre>{{ result.display_raw if result.display_raw is defined else result.raw }}</pre></details>
      </section>
    {% endif %}
    <footer>Compilers II · Academic project · Results generated by the real Java compiler</footer>
  </main>
</body>
</html>"""


# تُسجَّل واجهة الفحص على خادم Flask وتعرض النجاح أو التشخيصات حسب النتيجة الفعلية.
def register_compiler_validation(app):
    """واجهة الإصلاح المباشر في ملفات الاختبار وزر استعادة نسخة المصدر الأصلية."""
    @app.route("/compiler/validate", methods=["GET", "POST"])
    def compiler_validation_page():
        selected = "valid"
        result = None
        action_message = ""
        if request.method == "POST":
            if request.remote_addr not in ("127.0.0.1", "::1", None):
                abort(403)
            selected = request.form.get("project", "")
            if selected not in PROJECTS:
                abort(400)
            action = request.form.get("action", "run")
            if action not in ("run", "fix", "undo", "reset"):
                abort(400)
            # لا نسمح بتعديل ملفات مشروع الأخطاء إلا عبر نموذج محمي وخيارات محددة.
            if action != "run":
                if selected != "invalid" or not secrets.compare_digest(
                        request.form.get("csrf", ""), DEMO_FORM_TOKEN):
                    abort(403)
            if not RUN_LOCK.acquire(blocking=False):
                return render_template_string(PAGE, selected=selected,
                    result=unavailable_result(selected, "هناك تشغيل آخر للمترجم جارٍ. انتظر قليلًا ثم أعد المحاولة."),
                    csrf_token=DEMO_FORM_TOKEN, action_message=""), 409
            try:
                if action != "run":
                    try:
                        original = ROOT_PATH / "sample_project_invalid"
                        if action == "reset":
                            reset_workspace(ROOT_PATH, original)
                            action_message = "Restored the original sample_project_invalid files with all ten intentional errors. Java analysis was run again."
                        else:
                            key = request.form.get("key", "")
                            recipe = change_fix(ROOT_PATH, original, key, action)
                            action_message = ("Fixed " if action == "fix" else "Undid fix for ") + recipe.code + ". Recompiled the changed source."
                            if action == "fix" and key == "PY001":
                                action_message += " Note: correcting the Python name may reveal a previously masked JJ002 property error; this is normal semantic re-analysis."
                    except (ValueError, OSError, UnicodeError) as exc:
                        action_message = "Source was not changed: " + str(exc)
                # دائمًا تشغيل المترجم الفعلي بعد الضغط؛ نتائج الأخطاء لا تُصطنع من Flask.
                result = compile_selected(selected)
            finally:
                RUN_LOCK.release()
        return render_template_string(PAGE, selected=selected, result=result,
                                      csrf_token=DEMO_FORM_TOKEN, action_message=action_message)
