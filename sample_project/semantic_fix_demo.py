"""إصلاحات عرض توضيحي محدّدة لأخطاء مشروع الاختبار فقط.

تعمل أزرار الإصلاح والتراجع على ملفات sample_project_invalid نفسها.
تتوفر نسخة مرجعية ثابتة لاستعادة جميع الملفات قبل أي إصلاح.
"""
from __future__ import annotations

import json
import os
import shutil
import hashlib
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class FixRecipe:
    code: str
    name: str
    relative_file: str
    broken: str
    corrected: str
    description: str


# وصف تعديلات معروفة سلفًا على أخطاء اختبارية فقط؛ لا نقبل شيفرة أو مسارات من المستخدم.
RECIPES = {
    "PY005": FixRecipe("PY005", "InvalidDataShape", "app.py",
        '\"price\": "free",', '\"price\": 59.99,',
        "تبديل السعر النصي بقيمة رقمية ليقبلها الفحص الدلالي."),
    "PY001": FixRecipe("PY001", "UndefinedVariable", "app.py",
        'products=prodcts, categories=CATEGORIES',
        'products=products, categories=CATEGORIES',
        "تصحيح اسم المتغير إلى القائمة المعرّفة في المصدر."),
    "PY003": FixRecipe("PY003", "TemplateNotFound", "app.py",
        'render_template("confirm_delete.jinja", product=product)',
        'render_template("delete_product.jinja", product=product)',
        "استدعاء القالب الموجود فعليًا بدل اسم غير موجود."),
    "PY002": FixRecipe("PY002", "DuplicateRoute", "app.py",
        '@app.route("/", methods=["GET", "POST"])\ndef regenerate_site():',
        '@app.route("/manual-regenerate", methods=["GET", "POST"])\ndef regenerate_site():',
        "تخصيص مسار غير مكرر للدالة الثانية بدل تكرار المسار الرئيسي."),
    "PY004": FixRecipe("PY004", "MissingContextVariable", "app.py",
        'return render_template("product_detail.jinja")',
        'return render_template("product_detail.jinja", product=product)',
        "تمرير متغير المنتج إلى القالب الذي يستخدمه."),
    "JJ006": FixRecipe("JJ006", "UnknownFilter", "templates/add_product.jinja",
        '"demo"|not_a_real_filter', '"demo"|upper',
        "استبدال المرشح غير المدعوم بمرشح معروف."),
    "JJ003": FixRecipe("JJ003", "NonIterableLoop", "templates/add_product.jinja",
        '{% for category in categories|length %}',
        '{% for category in categories %}',
        "المرور على القائمة بدل المرور على عدد عناصرها."),
    "JJ002": FixRecipe("JJ002", "UnknownProperty", "templates/index.jinja",
        '<h2 class="product-card__name">{{ product.nam }}</h2>',
        '<h2 class="product-card__name">{{ product.name }}</h2>',
        "تصحيح اسم خاصية المنتج بعد اكتشاف خطأ كان مخفيًا بسبب متغير غير معرّف."),
    "JJ005": FixRecipe("JJ005", "VariableOutOfScope", "templates/index.jinja",
        '<p class="page-subtitle">Last product shown: {{ product.name }}</p>',
        '<p class="page-subtitle">Browse our latest products.</p>',
        "إزالة استخدام متغير الحلقة بعد الخروج من نطاقها."),
    "JJ004": FixRecipe("JJ004", "UnknownEndpoint", "templates/product_detail.jinja",
        '<nav class="breadcrumb">\n    <a href="{{ url_for(\'home\') }}">Products</a>',
        '<nav class="breadcrumb">\n    <a href="{{ url_for(\'index\') }}">Products</a>',
        "استدعاء اسم مسار Flask المعرّف بالفعل."),
    "JJ001": FixRecipe("JJ001", "UndefinedVariable", "templates/product_detail.jinja",
        '<span>{{ shop_name }} / {{ product.name }}</span>',
        '<span>ProductShop / {{ product.name }}</span>',
        "إظهار عنوان ثابت بدل متغير غير معرّف في سياق القالب."),
}



# نسخة مرجعية ثابتة من المشروع الخاطئ قبل أي إصلاح؛ تأتي مع أرشيف المشروع نفسه.
# تبقى هذه النسخة خارج sample_project_invalid حتى لا يغيرها زر إصلاح أو تشغيل المترجم.
BASELINE_DIR_NAME = "validation_baseline_invalid"
MANIFEST_NAME = "validation_baseline_invalid.sha256.json"


def workspace_paths(root: Path):
    """موقع حالة الإصلاحات فقط؛ لا توجد نسخة عمل منفصلة للمشروع الخاطئ."""
    base = root / "compiler_validation_runs" / "invalid"
    return root / "sample_project_invalid", base / "repair_state.json"


def _baseline_files(root: Path):
    """التحقق من سلامة النسخة المرجعية قبل استعادة ملفات الاختبار."""
    baseline = root / "sample_project" / BASELINE_DIR_NAME
    manifest = root / "sample_project" / MANIFEST_NAME
    if not baseline.is_dir() or not manifest.is_file():
        raise ValueError("Original invalid-project backup is missing")
    expected = json.loads(manifest.read_text(encoding="utf-8"))
    if not isinstance(expected, dict) or not expected:
        raise ValueError("Invalid original-source manifest")
    found = {p.relative_to(baseline).as_posix(): p for p in baseline.rglob("*") if p.is_file()}
    if set(expected) != set(found):
        raise ValueError("The original backup files do not match the manifest")
    for rel, path in found.items():
        if path.is_symlink() or hashlib.sha256(path.read_bytes()).hexdigest() != expected[rel]:
            raise ValueError("Original invalid-project backup failed integrity verification: " + rel)
    return baseline


def _write_state(path: Path, applied: list[str]):
    """تسجيل حالة واجهة الإصلاح فقط؛ المصدر الفعلي هو sample_project_invalid."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps({"applied": applied}, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)


def active_workspace(root: Path, original: Path):
    """إرجاع مجلد الأخطاء نفسه، لا نسخه إلى working_project كما كان سابقًا."""
    expected = (root / "sample_project_invalid").resolve()
    if original.resolve() != expected or not expected.is_dir():
        raise ValueError("Expected sample_project_invalid source directory is missing")
    return expected


def applied_fixes(root: Path):
    """اكتشاف الإصلاحات من الشيفرة الحقيقية كي لا تضيع الحالة عند إعادة تشغيل Flask."""
    source = root / "sample_project_invalid"
    active = []
    for recipe in RECIPES.values():
        path = source / recipe.relative_file
        if not path.is_file() or path.is_symlink():
            continue
        text = path.read_text(encoding="utf-8")
        if text.count(recipe.corrected) == 1 and recipe.broken not in text:
            active.append(recipe)
    return active


def reset_workspace(root: Path, original: Path):
    """استعادة ملفات sample_project_invalid كلها كما كانت، وليس التراجع عن زر واحد.

    ننسخ النسخة المرجعية إلى مجلد تحضير، ثم نستبدل مجلد المصدر بعد نجاح
    نسخ جميع الملفات. عند فشل الاستبدال نعيد المجلد السابق بدل فقدانه.
    """
    source = active_workspace(root, original)
    baseline = _baseline_files(root)
    with tempfile.TemporaryDirectory(prefix="invalid-project-restore-", dir=str(root)) as temp:
        scratch = Path(temp)
        prepared = scratch / "restored"
        previous = scratch / "previous"
        shutil.copytree(baseline, prepared, symlinks=False)
        # تأكيد أن النسخة المحضرة مطابقة للمرجع قبل لمس الملفات الأصلية.
        for file in baseline.rglob("*"):
            if file.is_file() and (prepared / file.relative_to(baseline)).read_bytes() != file.read_bytes():
                raise ValueError("Could not prepare a complete source restoration")
        os.replace(source, previous)
        try:
            os.replace(prepared, source)
        except OSError:
            os.replace(previous, source)
            raise
    _write_state(workspace_paths(root)[1], [])
    return source


def change_fix(root: Path, original: Path, key: str, direction: str):
    """إصلاح/تراجع مباشر في الملف الخاطئ الحقيقي، دون تعديل كود المترجم أو المشروع الصحيح."""
    if key not in RECIPES or direction not in ("fix", "undo"):
        raise ValueError("Unknown error or operation")
    source = active_workspace(root, original)
    currently = [recipe.code for recipe in applied_fixes(root)]
    present = key in currently
    if direction == "fix" and present:
        raise ValueError("This error has already been fixed")
    if direction == "undo" and not present:
        raise ValueError("This fix is not active")
    recipe = RECIPES[key]
    file = source / recipe.relative_file
    if not file.is_file() or file.is_symlink():
        raise ValueError("Expected original source file is missing")
    content = file.read_bytes().decode("utf-8")
    before = recipe.broken if direction == "fix" else recipe.corrected
    after = recipe.corrected if direction == "fix" else recipe.broken
    if content.count(before) != 1:
        raise ValueError("The source has changed; use Restore Original or inspect the source")
    updated = content.replace(before, after, 1).encode("utf-8")
    temporary = file.with_name(file.name + ".fix-tmp")
    try:
        temporary.write_bytes(updated)
        os.replace(temporary, file)
    finally:
        temporary.unlink(missing_ok=True)
    _write_state(workspace_paths(root)[1], [item.code for item in applied_fixes(root)])
    return recipe
