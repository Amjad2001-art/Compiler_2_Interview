# Semantic fixture PY004 MissingContextVariable:
# index.jinja needs `products`, but the route forgets to pass it
# (a Python variable called `products` exists, so the mistake is on the Python side).
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", categories=CATEGORIES)
