# Semantic fixture PY003 TemplateNotFound:
# render_template() refers to "catalog.jinja", which does not exist in templates/.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("catalog.jinja", products=products, categories=CATEGORIES)
