"""
ProductShop - the Flask TARGET application.

This file is the ANALYSED INPUT of the Java compiler (compiler-java). The Java
compiler lexes and parses it, builds the Python AST, checks it semantically,
evaluates the product data below and binds it to the Jinja templates in
templates/ to generate the connected static pages in output/.

At runtime this is also a normal Flask application (python app.py). Adding,
editing or deleting a product rewrites the PRODUCT DATA block below and re-runs
the Java compiler (see regenerate.py), so the generated pages stay in sync.
"""
from flask import Flask, render_template, request, redirect, url_for, abort, send_from_directory
from regenerate import save_and_regenerate, OUTPUT_DIR

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
        "price": 89.99,
        "category": "Accessories",
        "details": "Full-size mechanical keyboard with RGB backlight, tactile switches and programmable macro keys.",
        "image": "https://placehold.co/400x300/06b6d4/ffffff?text=Mech+Keyboard"
    },
    {
        "id": 3,
        "name": "4K Webcam Ultra",
        "price": "free",
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
def index():
    return render_template("index.jinja", products=prodcts, categories=CATEGORIES) # type: ignore


@app.route("/product/<int:product_id>")
def product_detail(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("product_detail.jinja")


@app.route("/add", methods=["GET", "POST"])
def add_product():
    if request.method == "POST":
        data = read_product_form(request.form)
        if data["name"] == "" or data["price"] < 0:
            return redirect(url_for("add_product"))
        data["id"] = next_product_id()
        products.append(data)
        save_and_regenerate(products)
        return redirect(url_for("index"))
    return render_template("add_product.jinja", categories=CATEGORIES)


@app.route("/edit/<int:product_id>", methods=["GET", "POST"])
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
        save_and_regenerate(products)
        return redirect(url_for("product_detail", product_id=product_id))
    return render_template("edit_product.jinja", product=product, categories=CATEGORIES)


@app.route("/delete/<int:product_id>", methods=["GET", "POST"])
def delete_product(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    if request.method == "POST":
        products.remove(product)
        save_and_regenerate(products)
        return redirect(url_for("index"))
    return render_template("confirm_delete.jinja", product=product)


@app.route("/output/<path:filename>")
def generated_site(filename):
    return send_from_directory(OUTPUT_DIR, filename)


@app.route("/", methods=["GET", "POST"])
def regenerate_site():
    save_and_regenerate(products)
    return render_template("delete_product.jinja", product=products[0])


if __name__ == "__main__":
    app.run(debug=True)
