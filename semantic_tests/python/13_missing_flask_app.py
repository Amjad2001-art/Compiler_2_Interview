# Semantic fixture PY014 MissingFlaskApp:
# `app` is a plain string, not a Flask application, so @app.route is meaningless.
from flask import Flask, render_template

app = "ProductShop"

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)
