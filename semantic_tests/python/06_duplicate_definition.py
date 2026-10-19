# Semantic fixture PY006 DuplicateDefinition:
# the helper function find_product is defined twice.
from flask import Flask, render_template, abort

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


def find_product(product_id):
    for product in products:
        if product["id"] == product_id:
            return product
    return None


def find_product(product_id):
    return products[0]


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)


@app.route("/product/<int:product_id>")
def product_detail(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("product_detail.jinja", product=product)
