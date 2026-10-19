# Semantic fixture PY007 DuplicateDictionaryKey:
# the first product defines the key "price" twice.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "price": 350, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)
