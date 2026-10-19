# Semantic fixture PY002 DuplicateRoute:
# two view functions handle the same URL rule "/" with the same HTTP method GET.
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/")
def index():
    return render_template("index.jinja", products=products, categories=CATEGORIES)


@app.route("/", methods=["GET"])
def home():
    return render_template("index.jinja", products=products, categories=CATEGORIES)
