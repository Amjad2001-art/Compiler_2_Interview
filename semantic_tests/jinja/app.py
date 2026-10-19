"""
Context provider for the Jinja semantic fixtures.

This small Flask application is itself valid. Each route renders one fixture
template with a well-defined context, so the compiler can check every fixture
against real Python data (java -jar compiler.jar semantic_tests/jinja --check-only).
"""
from flask import Flask, render_template, abort

app = Flask(__name__)

CATEGORIES = ["Audio", "Computers"]

products = [
    {"id": 1, "name": "Phone", "price": 300, "category": "Audio"},
    {"id": 2, "name": "Laptop", "price": 800, "category": "Computers"}
]

shop_name = "ProductShop"


def find_product(product_id):
    for product in products:
        if product["id"] == product_id:
            return product
    return None


@app.route("/")
def index():
    return render_template("01_undefined_variable.jinja", products=products, categories=CATEGORIES)


@app.route("/product/<int:product_id>")
def product_detail(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("02_invalid_property.jinja", product=product)


@app.route("/fixture/03")
def fixture_03():
    return render_template("03_non_iterable_loop.jinja", shop_name=shop_name, products=products)


@app.route("/fixture/04")
def fixture_04():
    return render_template("04_unknown_endpoint.jinja", products=products)


@app.route("/fixture/05")
def fixture_05():
    return render_template("05_variable_out_of_scope.jinja", products=products)


@app.route("/fixture/06/<int:product_id>")
def fixture_06(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("06_unknown_filter.jinja", product=product)


@app.route("/fixture/07")
def fixture_07():
    return render_template("07_url_for_parameter_mismatch.jinja", products=products)


@app.route("/fixture/08/<int:product_id>")
def fixture_08(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("08_unknown_test.jinja", product=product)


@app.route("/fixture/09")
def fixture_09():
    return render_template("09_filter_argument_mismatch.jinja", products=products)


@app.route("/fixture/10")
def fixture_10():
    return render_template("10_static_file_not_found.jinja", products=products)


@app.route("/fixture/11/<int:product_id>")
def fixture_11(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("11_unknown_function.jinja", product=product)


@app.route("/fixture/12/<int:product_id>")
def fixture_12(product_id):
    product = find_product(product_id)
    if product is None:
        abort(404)
    return render_template("12_incompatible_comparison.jinja", product=product)


@app.route("/fixture/13")
def fixture_13():
    return render_template("13_form_method_mismatch.jinja", categories=CATEGORIES)


@app.route("/fixture/14")
def fixture_14():
    return render_template("14_duplicate_html_id.jinja", products=products)
