"""
Runtime bridge between the Flask application and the Java compiler.

This module is NOT part of the compiler and is NOT analysed by it. It does two
things when the user adds, edits or deletes a product through the Flask app:

1. save_products(products): rewrites the PRODUCT DATA block inside app.py so
   the Python source stays the single source of truth for the product data;
2. run_compiler(): re-runs the Java compiler (compiler-java/target/compiler.jar)
   on this project so the generated pages in output/ reflect the new data.

All paths are relative to this file; nothing is hard-coded to a machine.
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
APP_FILE = os.path.join(HERE, "app.py")
# Defaults are relative to this repository; the FJC_* environment variables
# allow running the application from a copied/deployed location.
JAR = os.environ.get("FJC_COMPILER_JAR", os.path.join(ROOT, "compiler-java", "target", "compiler.jar"))
OUTPUT_DIR = os.environ.get("FJC_OUTPUT_DIR", os.path.join(ROOT, "output"))
COMPILER_OUTPUT_DIR = os.environ.get("FJC_COMPILER_OUTPUT_DIR", os.path.join(ROOT, "compiler_output"))

BEGIN_MARKER = "# --- PRODUCT DATA (managed by the application; rewritten on add/edit/delete) ---"
END_MARKER = "# --- END PRODUCT DATA ---"
FIELD_ORDER = ["id", "name", "price", "category", "details", "image"]


def _literal(value):
    """Python source text for a product field value (only str/int/float are used)."""
    if isinstance(value, bool):
        return "True" if value else "False"
    if isinstance(value, (int, float)):
        return repr(value)
    return json.dumps(str(value), ensure_ascii=False)


def format_products(products):
    """Renders the product list as Python source (a list of dictionary literals)."""
    lines = ["products = ["]
    for index, product in enumerate(products):
        lines.append("    {")
        keys = [k for k in FIELD_ORDER if k in product] + [k for k in product if k not in FIELD_ORDER]
        for position, key in enumerate(keys):
            comma = "," if position < len(keys) - 1 else ""
            lines.append('        "%s": %s%s' % (key, _literal(product[key]), comma))
        lines.append("    }" + ("," if index < len(products) - 1 else ""))
    lines.append("]")
    return "\n".join(lines)


# عند الحفظ تُكتب قائمة المنتجات إلى كتلة البيانات في مصدر بايثون بطريقة استبدال آمن للملف.
def save_products(products):
    """Rewrites the PRODUCT DATA block of app.py atomically."""
    with open(APP_FILE, "r", encoding="utf-8") as handle:
        source = handle.read()
    start = source.index(BEGIN_MARKER) + len(BEGIN_MARKER)
    end = source.index(END_MARKER)
    new_source = source[:start] + "\n" + format_products(products) + "\n" + source[end:]
    tmp = APP_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(new_source)
    os.replace(tmp, APP_FILE)


# عند طلب تحديث الصفحات يشغّل التطبيق نفس مترجم جافا على المشروع ويدقق رمز الخروج.
def run_compiler():
    """Runs the Java compiler on this project. Returns the process exit code (0 = success)."""
    if not os.path.exists(JAR):
        print("[regenerate] compiler jar not found: %s (run 'mvn clean package' in compiler-java)" % JAR, file=sys.stderr)
        return 127
    # هذه هي تعليمة إعادة التوليد الفعلية؛ تعيد بناء الصفحات والتقارير من المصدر الحالي.
    command = ["java", "-jar", JAR, HERE, "--output", OUTPUT_DIR, "--compiler-output", COMPILER_OUTPUT_DIR, "--quiet"]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print("[regenerate] Java compiler failed (exit %d); see %s" % (result.returncode, os.path.join(COMPILER_OUTPUT_DIR, "semantic_report.txt")), file=sys.stderr)
        if result.stdout:
            print(result.stdout, file=sys.stderr)
    else:
        print("[regenerate] generated pages refreshed in %s" % OUTPUT_DIR, file=sys.stderr)
    return result.returncode


# يأتي ترتيب الحفظ أولا ثم إعادة التوليد حتى تُستخدم البيانات المحدّثة في ملفات الخرج.
def save_and_regenerate(products):
    """Persist the products into app.py and regenerate the static pages."""
    save_products(products)
    return run_compiler()
