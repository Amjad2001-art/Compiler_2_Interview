# Semantic fixture PY015 RouteWithoutReturn:
# the view function renders the template but never returns the response.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    render_template("index.jinja", products=products, categories=CATEGORIES)
