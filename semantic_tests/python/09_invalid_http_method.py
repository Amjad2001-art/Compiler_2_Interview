# Semantic fixture PY009 InvalidHttpMethod:
# "PSOT" is not an HTTP method.
from flask import Flask, render_template, request, redirect, url_for

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]


@app.route("/", methods=["GET", "PSOT"])
def index():
    if request.method == "POST":
        return redirect(url_for("index"))
    return render_template("index.jinja", products=products, categories=CATEGORIES)
