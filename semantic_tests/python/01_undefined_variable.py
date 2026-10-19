# Semantic fixture PY001 UndefinedVariable:
# the route passes `prodcts` (a typo) to the template; that name is never defined.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", products=prodcts, categories=CATEGORIES) # type: ignore
