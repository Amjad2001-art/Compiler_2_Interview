# Semantic fixture PY005 InvalidDataShape:
# `products` must be a list of dictionaries with consistent keys; here the second
# element is a plain string and the third product has a string price.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    "Laptop",
    {"id": 3, "name": "Tablet", "price": "cheap", "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)
