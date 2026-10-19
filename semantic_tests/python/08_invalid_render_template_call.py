# Semantic fixture PY008 InvalidRenderTemplateCall:
# the template name is a variable instead of a string literal, so the compiler
# cannot know which template is rendered (and cannot check its context).
from flask import Flask, render_template

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]

page = "index.jinja"


@app.route("/")
def index():
    return render_template(page, products=products, categories=CATEGORIES)
