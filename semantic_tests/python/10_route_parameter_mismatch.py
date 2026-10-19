# Semantic fixture PY010 RouteParameterMismatch:
# the URL rule declares <int:product_id> but the view function takes `pid`.
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


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)


@app.route("/product/<int:product_id>")
def product_detail(pid):
    product = find_product(pid)
    if product is None:
        abort(404)
    return render_template("product_detail.jinja", product=product)
