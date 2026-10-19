"""
ProductShop - the Flask TARGET application.

This file is the ANALYSED INPUT of the Java compiler (compiler-java). The Java
compiler lexes and parses it, builds the Python AST, checks it semantically,
evaluates the product data below and binds it to the Jinja templates in
templates/ to generate the connected static pages in output/.

At runtime this is also a normal Flask application (python app.py). Adding, editing or deleting products on the ORIGINAL Flask site saves the
PRODUCT DATA block below without regenerating generated HTML. The user runs
the Java compiler explicitly to synchronize output/. Generated-site requests
retain their own working CRUD flow and regenerate the output after a change.
"""
from flask import Flask, render_template, request, redirect, url_for, abort, send_from_directory
from regenerate import save_products, save_and_regenerate, OUTPUT_DIR
from tree_viewer import register_tree_viewer
# واجهة أكاديمية إضافية لتشغيل المترجم الحقيقي وإظهار الأخطاء الدلالية.
from compiler_validation import register_compiler_validation

app = Flask(__name__)
app.secret_key = "productshop-dev-key"
app.jinja_env.autoescape = True

CATEGORIES = ["Audio", "Computers", "Accessories", "Displays"]

# --- PRODUCT DATA (managed by the application; rewritten on add/edit/delete) ---
products = [
    {
        "id": 1,
        "name": "Wireless Headphones Pro",
        "price": 129.99,
        "category": "Audio",
        "details": "Premium over-ear wireless headphones with active noise cancellation, 30-hour battery life and studio-quality sound.",
        "image": "https://placehold.co/400x300/4f46e5/ffffff?text=Headphones+Pro"
    },
    {
        "id": 2,
        "name": "Gaming Mechanical Keyboard",
        "price": 90.0,
        "category": "Accessories",
        "details": "Full-size mechanical keyboard with RGB backlight, tactile switches and programmable macro keys.",
        "image": "https://placehold.co/400x300/06b6d4/ffffff?text=Mech+Keyboard"
    },
    {
        "id": 3,
        "name": "4K Webcam Ultra",
        "price": 74.99,
        "category": "Accessories",
        "details": "4K resolution webcam with autofocus, built-in stereo microphone and low-light correction.",
        "image": "https://placehold.co/400x300/16a34a/ffffff?text=4K+Webcam"
    },
    {
        "id": 4,
        "name": "Gaming Laptop",
        "price": 1299.99,
        "category": "Computers",
        "details": "High-performance laptop with a dedicated GPU, 16 GB RAM and a 1 TB SSD.",
        "image": "https://placehold.co/400x300/f97316/ffffff?text=Gaming+Laptop"
    },
    {
        "id": 5,
        "name": "Smart TV 55",
        "price": 799.99,
        "category": "Displays",
        "details": "55 inch 4K OLED display with HDMI 2.1, Dolby Vision and a 120 Hz panel.",
        "image": "https://placehold.co/400x300/9333ea/ffffff?text=Smart+TV"
    },
    {
        "id": 6,
        "name": "Bag",
        "price": 29.95,
        "category": "Accessories",
        "details": "",
        "image": "https://tse3.mm.bing.net/th/id/OIP.5FBuoAIK3CbVb_Kh3uzICQHaEO?r=0&rs=1&pid=ImgDetMain&o=7&rm=3"
    },
    {
        "id": 7,
        "name": "4k Screen",
        "price": 78.0,
        "category": "Displays",
        "details": "",
        "image": "https://tse1.mm.bing.net/th/id/OIP.NO87V07BGKVGh-TNW6uJPAHaHa?r=0&w=500&h=500&rs=1&pid=ImgDetMain&o=7&rm=3"
    }
]
# --- END PRODUCT DATA ---


def find_product(product_id):
    for product in products:
        if product["id"] == product_id:
            return product
    return None


def next_product_id():
    highest = 0
    for product in products:
        if product["id"] > highest:
            highest = product["id"]
    return highest + 1


def read_product_form(form):
    name = form.get("name", "").strip()
    price_text = form.get("price", "").strip()
    category = form.get("category", "Accessories").strip()
    details = form.get("details", "").strip()
    image = form.get("image", "").strip()
    price = -1.0
    if price_text != "":
        price = float(price_text)
    if category not in CATEGORIES:
        category = "Accessories"
    if image == "":
        image = "https://placehold.co/400x300?text=No+Image"
    return {"name": name, "price": price, "category": category, "details": details, "image": image}


@app.route("/")
# هذا مسار عرض قائمة المنتجات؛ يمرر القائمة والتصنيفات إلى قالب الفهرس.
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)


@app.route("/product/<int:product_id>")
# مسار التفاصيل يمرر بيانات المنتج المختار إلى القالب عبر وسيط سياق صريح.
def product_detail(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("product_detail.jinja", product=product)


# الفرق بين نمطي العمل: الموقع الأصلي يحفظ البيانات فقط، والموقع المولد يعيد التوليد بعد الحفظ.
def persist_product_changes():
    """Save both site modes; only generated-site submissions regenerate.

    The source Flask app at / persists edits without rewriting output/.
    Generated HTML forms submit a `_generated_site=1` marker through their
    copied script.js; those edits must regenerate the static HTML immediately
    so their own interface stays interactive and consistent.
    """
    if request.form.get("_generated_site") == "1":
        if save_and_regenerate(products) != 0:
            abort(500)
        return redirect(url_for("generated_site", filename="index.html"))
    save_products(products)
    return redirect(url_for("index"))


@app.route("/add", methods=["GET", "POST"])
# واجهة الإضافة تقرأ نموذج المستخدم وتتحقق من الاسم والسعر قبل إدراج المنتج.
def add_product():
    if request.method == "POST":
        data = read_product_form(request.form)
        if data["name"] == "" or data["price"] < 0:
            return redirect(url_for("add_product"))
        data["id"] = next_product_id()
        products.append(data)
        return persist_product_changes()
    return render_template("add_product.jinja", categories=CATEGORIES)


@app.route("/edit/<int:product_id>", methods=["GET", "POST"])
# واجهة التعديل تحفظ الحقول الجديدة في مصدر بيانات التطبيق.
def edit_product(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    if request.method == "POST":
        data = read_product_form(request.form)
        if data["name"] == "" or data["price"] < 0:
            return redirect(url_for("edit_product", product_id=product_id))
        product["name"] = data["name"]
        product["price"] = data["price"]
        product["category"] = data["category"]
        product["details"] = data["details"]
        product["image"] = data["image"]
        return persist_product_changes()
    return render_template("edit_product.jinja", product=product, categories=CATEGORIES)


@app.route("/delete/<int:product_id>", methods=["GET", "POST"])
# واجهة الحذف تزيل المنتج بعد طلب التأكيد ثم تحفظ التغيير.
def delete_product(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    if request.method == "POST":
        products.remove(product)
        return persist_product_changes()
    return render_template("delete_product.jinja", product=product)


@app.route("/output/<path:filename>")
# الصفحات المولدة تُعرض من مجلد الخرج عبر خادم Flask حتى تعمل مساراتها بشكل صحيح.
def generated_site(filename):
    return send_from_directory(OUTPUT_DIR, filename)


@app.route("/regenerate", methods=["POST"])
def regenerate_site():
    # Retained for backward compatibility. Product edits made on the original
    # Flask pages never call this; they require an EXPLICIT compiler run.
    if save_and_regenerate(products) != 0:
        abort(500)
    return redirect(url_for("index"))


# هذه الإضافة تسجل عارض أشجار المترجم دون تغيير تحليل Java أو توليد الصفحات.
register_tree_viewer(app)
# نضيف مسار الواجهة فقط؛ لا نغيّر أي مسار CRUD أو مرحلة داخل مترجم Java.
# واجهة التحقق تستدعي المترجم الحقيقي، وتعرض نتائج التشغيل دون إنشاء قواعد دلالية بديلة.
register_compiler_validation(app)


if __name__ == "__main__":
    app.run(debug=False, use_reloader=False)
