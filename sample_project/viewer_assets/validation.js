// منع النقر المتكرر أثناء انتظار تشغيل مترجم Java الحقيقي.
const form = document.getElementById("run-form");
if (form) form.addEventListener("submit", () => {
  const button = document.getElementById("run-button");
  button.disabled = true;
  button.textContent = "Running Java compiler...";
});

// منع الضغط المكرر على إصلاح الخطأ أو التراجع أثناء إعادة تحليل المشروع.
document.querySelectorAll(".fix-form, .reset-form").forEach(form => {
  form.addEventListener("submit", () => {
    form.querySelectorAll("button").forEach(button => { button.disabled = true; button.textContent = "Compiling..."; });
  });
});
