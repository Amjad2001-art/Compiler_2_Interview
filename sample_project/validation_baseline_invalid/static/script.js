/*
 * ProductShop client script - COPIED SUPPORT FILE (never generated).
 *
 * 1. Generated static pages for parameterized routes (product_detail.html,
 *    edit_product.html, delete_product.html) contain one <div class="page-instance">
 *    per product. The instance whose data-page-key matches the query string
 *    (e.g. ?product_id=2) is shown; the others stay hidden.
 * 2. Forms with a data-confirm attribute ask for confirmation before submitting.
 * 3. The add/edit forms show a live preview of the image URL.
 */
(function () {
  "use strict";

  function selectPageInstance() {
    var instances = document.querySelectorAll(".page-instance");
    if (instances.length === 0) {
      return;
    }
    var key = window.location.search.replace(/^\?/, "");
    var shown = null;
    instances.forEach(function (instance) {
      var matches = key !== "" && decodeURIComponent(key) === instance.getAttribute("data-page-key");
      if (matches && shown === null) {
        shown = instance;
      }
    });
    if (shown === null && key === "") {
      shown = instances[0];
    }
    instances.forEach(function (instance) {
      if (instance === shown) {
        instance.removeAttribute("hidden");
      } else {
        instance.setAttribute("hidden", "");
      }
    });
    if (shown !== null) {
      var title = shown.getAttribute("data-page-title");
      if (title) {
        document.title = title;
      }
    } else {
      var notice = document.createElement("p");
      notice.className = "page-instance-missing";
      notice.textContent = "No generated page matches '" + key + "'. Go back to the product list.";
      var back = document.createElement("a");
      back.href = "index.html";
      back.textContent = " Back to products";
      notice.appendChild(back);
      document.body.insertBefore(notice, document.body.firstChild);
    }
  }

  function installConfirmations() {
    document.querySelectorAll("form[data-confirm]").forEach(function (form) {
      form.addEventListener("submit", function (event) {
        if (!window.confirm(form.getAttribute("data-confirm"))) {
          event.preventDefault();
        }
      });
    });
  }

  function installImagePreview() {
    document.querySelectorAll(".page-instance, body").forEach(function (scope) {
      var input = scope.querySelector("#image");
      var group = scope.querySelector("#preview-group");
      var preview = scope.querySelector("#image-preview");
      if (!input || !group || !preview) {
        return;
      }
      function update() {
        var url = input.value.trim();
        if (url) {
          preview.src = url;
          group.removeAttribute("hidden");
        } else {
          group.setAttribute("hidden", "");
        }
      }
      input.addEventListener("input", update);
      preview.addEventListener("error", function () {
        preview.src = "https://placehold.co/400x300?text=Invalid+URL";
      });
      update();
    });
  }

  selectPageInstance();
  installConfirmations();
  installImagePreview();
})();
