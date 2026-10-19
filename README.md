# مشروع المترجمات 2

**Flask / Jinja Compiler — Java 17**

مترجم تعليمي مكتوب بلغة Java يحلل مشروع ويب يعتمد على Python/Flask وقوالب Jinja2 مع HTML وCSS. يمرّ بمراحل التحليل اللفظي والنحوي، وبناء الأشجار التجريدية، والتحليل الدلالي، وتجهيز بيانات السياق، ثم يولّد صفحات HTML مترابطة لإدارة المنتجات.

> **مهم:** المترجم نفسه مكتوب بلغة **Java**. أما Python/Flask فهو مشروع الإدخال الذي يحلّله المترجم، كما يُستخدم لتشغيل واجهات الويب وعرض النتائج. التعليمات التالية موجّهة لنظام **Windows / PowerShell**، وتُنفّذ من جذر المستودع ما لم يُذكر خلاف ذلك.

## 1. المتطلبات المسبقة

- **JDK 17 أو أحدث** مع توفر `java` و`javac` في متغير البيئة `PATH`؛ المشروع مبني ليستهدف Java 17.
- **Python 3.10 أو أحدث** مع توفر الأمر `python`.
- **Git** لتنزيل المستودع، واتصال بالإنترنت في أول عملية بناء لتنزيل تبعيات Maven وFlask.
- لا حاجة لتثبيت Maven يدويًا عند استخدام ملف **Maven Wrapper** المرفق `compiler-java/mvnw.cmd`.

تأكد من توفر الأدوات:

```powershell
java -version
javac -version
python --version
git --version
```

## 2. تنزيل السورس كود من GitHub

افتح **PowerShell** في المجلد الذي تريد تنزيل المشروع داخله، ثم نفّذ:

```powershell
git clone https://github.com/Amjad2001-art/Compiler_2_Interview.git
cd .\Compiler_2_Interview
```

> لا يحتوي المستودع على ملف `compiler.jar` أو صفحات HTML أو تقارير توليد مسبقة؛ هذه مخرجات تُنشأ محليًا من المصدر في الخطوات التالية.

## 3. بناء مترجم Java من السورس

من داخل جذر المشروع، نفّذ الأوامر **بالترتيب**:

```powershell
cd .\compiler-java
.\mvnw.cmd clean package
cd ..
```

تنفّذ هذه العملية بناء Java واختبارات Maven المضمنة، ثم تنشئ الملف:

```text
compiler-java/target/compiler.jar
```

إذا كنت تستخدم Maven المثبت على جهازك بدلًا من Wrapper، يمكنك تنفيذ `mvn clean package` من داخل `compiler-java`.

**تنبيه:** يجب أن ينجح هذا البناء أولًا؛ لأن تشغيل المترجم من الطرفية وزر **Compiler Validation** وإعادة التوليد تعتمد كلها على ملف JAR الناتج.

## 4. تثبيت متطلبات موقع Python/Flask

ارجع إلى **جذر المستودع**، ثم نفّذ:

```powershell
python -m pip install -r .\sample_project\requirements.txt
```

متطلب التطبيق الأساسي هو Flask. ويمكن استخدام بيئة Python افتراضية بدل التثبيت العام (اختياري):

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r .\sample_project\requirements.txt
```

في حالة البيئة الافتراضية، استعمل ` .\.venv\Scripts\python.exe ` بدل `python` لتشغيل الموقع.

## 5. تشغيل المترجم وتوليد الصفحات لأول مرة

من **جذر المستودع**، نفّذ:

```powershell
java -jar .\compiler-java\target\compiler.jar .\sample_project
```

عند نجاح التحليل الدلالي ستظهر نتيجة النجاح، وتُولَّد صفحات HTML التالية في مجلد `output`:

```text
output/
├── index.html
├── add_product.html
├── edit_product.html
├── delete_product.html
├── product_detail.html
├── app.py             (نسخة داعمة دون تعديل)
├── style.css          (نسخة داعمة دون تعديل)
└── script.js          (نسخة داعمة دون تعديل)
```

كما يُنشئ المترجم **أربعة ملفات رسمية فقط** في مجلد `compiler_output`:

```text
compiler_output/
├── ast_python.json
├── ast_jinja.json
├── semantic_report.txt
└── generation_log.txt
```

ويحفظ الملفات التحليلية الإضافية (الأشجار النصية، جدول الرموز، معلومات السياق وغيرها) في مجلد مستقل اسمه `compiler_details`. تُنتَج هذه الملفات محليًا، ولا يلزم رفعها إلى GitHub.

## 6. تشغيل الموقع في المتصفح

بعد بناء Java وتثبيت Flask وإجراء أول توليد ناجح، شغّل خادم Flask من **جذر المستودع**:

```powershell
python .\sample_project\app.py
```

افتح الروابط التالية في متصفحك:

| الرابط | الوظيفة |
|---|---|
| `http://127.0.0.1:5000/` | الموقع الأصلي لإدارة المنتجات |
| `http://127.0.0.1:5000/output/index.html` | الموقع الناتج عن المترجم (HTML المولّد) |
| `http://127.0.0.1:5000/compiler/trees` | عرض أشجار التحليل وجدول الرموز |
| `http://127.0.0.1:5000/compiler/validate` | تشغيل المترجم وفحص الأخطاء دلاليًا من واجهة تفاعلية |

أوقف الخادم بالضغط على `Ctrl + C` داخل نافذة PowerShell التي تشغّله.

### الفرق بين الموقع الأصلي والموقع المولّد

- **الموقع الأصلي (`/`)**: إضافة منتج أو تعديله أو حذفه تحفظ البيانات في `sample_project/app.py`، ثم تعود إلى الصفحة الرئيسية. لتحديث ملفات `output` بعد هذه العمليات، أعد تنفيذ تعليمة Java في الخطوة الخامسة يدويًا.
- **الموقع المولّد (`/output/index.html`)**: عمليات إضافة/تعديل/حذف المنتجات تُجري الحفظ ثم تطلب إعادة تشغيل مترجم Java تلقائيًا، فتتحدث صفحات HTML المولّدة.
- أزرار **Compiler Trees** و**Compiler Validation** مخصّصة للموقع الأصلي، ولا تظهر في شريط تنقل الصفحات المولّدة.

## 7. تجربة الأخطاء الدلالية والإصلاح والتراجع

من الموقع الأصلي افتح **Compiler Validation**، أو استخدم الرابط المباشر:

```text
http://127.0.0.1:5000/compiler/validate
```

1. اختر **Valid Project** لتشغيل مترجم Java على `sample_project`؛ المتوقع نجاح التوليد وعرض الملفات الناتجة.
2. اختر **Invalid Project** لتشغيله على `sample_project_invalid`؛ في حالته الأصلية المتوقعة يحتوي **خمسة أخطاء Python وخمسة أخطاء Jinja**، وتتوقف مرحلة توليد HTML.
3. استخدم **Fix Error** لإصلاح الخطأ المحدد داخل ملف المصدر الفعلي في `sample_project_invalid`، ثم إعادة التحليل.
4. استخدم **Undo** لإرجاع الإصلاح المحدد وإعادة إظهار الخطأ عند الفحص.
5. استخدم **Restore Original · All 10 Errors** لاستعادة ملفات `sample_project_invalid` الثمانية إلى حالتها المرجعية الأصلية بالأخطاء العشرة. **تنبيه:** الاستعادة تستبدل التعديلات اليدوية التي أُجريت على مشروع الاختبار الخاطئ.

يعتمد زر الاستعادة على الملفات المرجعية المضمنة داخل:

```text
sample_project/validation_baseline_invalid/
sample_project/validation_baseline_invalid.sha256.json
```

**ملاحظة:** قد يؤدي إصلاح خطأ إلى كشف خطأ آخر كان محجوبًا، ولذلك يتم دائمًا عرض التشخيصات الحقيقية الناتجة من إعادة تشغيل Java، لا أعداد أخطاء افتراضية. وتُعزل تقارير الفحص الخاطئ في `compiler_validation_runs/invalid/` لحماية مخرجات المشروع الصحيح.

### إجراء الفحص الدلالي من PowerShell أيضًا

```powershell
java -jar .\compiler-java\target\compiler.jar .\sample_project_invalid --check-only --ascii
```

في الحالة الأصلية للمشروع الخاطئ، النتيجة المتوقعة هي `RESULT: FAILURE` مع `CODE GENERATION ABORTED: semantic errors`؛ **هذا فشل مقصود للاختبار وليس فشلًا في تشغيل البرنامج**. إن كنت أصلحت أخطاء من الواجهة، استخدم **Restore Original** أولًا لاستعادة حالة الاختبار الأصلية.

## 8. طباعة الأشجار وجدول الرموز

لعرض أشجار AST الخاصة بـPython وJinja وCSS مع جدول الرموز داخل الطرفية، من دون توليد HTML جديد:

```powershell
java -jar .\compiler-java\target\compiler.jar .\sample_project --check-only --print-trees
```

ويمكن الاطلاع على التقارير النصية الناتجة:

```powershell
Get-Content .\compiler_details\symbol_table.txt -Encoding UTF8
Get-Content .\compiler_details\ast_python_tree.txt -Encoding UTF8
Get-Content .\compiler_details\ast_jinja_tree.txt -Encoding UTF8
```

**مهم:** جدول الرموز يُستخدم في الفحص الدلالي، **ولا يُستخدم مصدرًا لبيانات المنتجات أثناء توليد HTML**؛ مصدر بيانات التوليد هو سياق الصفحات المُستخرج من Python AST.

## 9. تشغيل الاختبارات البرمجية

لاختبار Java عبر Maven:

```powershell
cd .\compiler-java
.\mvnw.cmd test
cd ..
```

ولتشغيل حزمة الأمثلة الدلالية المستقلة:

```powershell
java -jar .\compiler-java\target\compiler.jar --semantic-tests .\semantic_tests
```

يحتوي `semantic_tests` على **14 اختبارًا لبايثون** و**14 اختبارًا لجينجا**. كما يمكن فحص المشروع الصحيح أو الخاطئ منفردًا من واجهة **Compiler Validation**.

## 10. بنية المستودع البرمجية

```text
Compiler_2_Interview/
├── compiler-java/
│   ├── pom.xml                 إعداد Maven والاعتمادات
│   ├── mvnw.cmd                بناء المشروع على Windows
│   └── src/
│       ├── main/java/fjc/     Lexer، Parser، AST، Semantic، Code Generation
│       └── test/java/         اختبارات Java
├── sample_project/
│   ├── app.py                  تطبيق Flask الصحيح وبيانات المنتجات
│   ├── templates/              قوالب Jinja وHTML
│   ├── static/                 CSS وJavaScript
│   ├── regenerate.py           حفظ البيانات وإعادة تشغيل Java
│   ├── tree_viewer.py          واجهة عرض الأشجار والرموز
│   ├── compiler_validation.py واجهة تشغيل التحليل وإظهار نتائجه
│   ├── semantic_fix_demo.py    إصلاح الأخطاء والتراجع والاستعادة
│   ├── validation_baseline_invalid/  مصدر الاستعادة المرجعي
│   ├── validation_baseline_invalid.sha256.json
│   ├── viewer_assets/          ملفات واجهات الفحص والأشجار
│   └── requirements.txt        متطلبات Flask
├── sample_project_invalid/    مشروع يحتوي الأخطاء الدلالية المقصودة
├── semantic_tests/            اختبارات دلالية مستقلة
└── .gitignore                 استبعاد المخرجات والملفات غير المصدرية
```

## 11. العلاقة بين المراحل ومتطلبات المشروع

| المرحلة | تنفيذها في المشروع |
|---|---|
| التحليل اللفظي والنحوي | محللات Java تقرأ Python وJinja/HTML وCSS |
| بناء الأشجار | بناء Python AST وJinja AST، وتمثيل إضافي لـCSS |
| البرمجة الكائنية | عقد AST تستخدم الوراثة وتعدد الأشكال وآلية Visitor |
| التحليل الدلالي | فحص أخطاء Python وJinja والتحقق من توافق السياق مع القوالب |
| تجهيز البيانات | استخراج بيانات المنتجات من Python وإعداد سياقات الصفحات |
| التوليد | معالجة Jinja AST باستخدام السياق، ثم كتابة ملفات HTML |
| نسخ الملفات المساندة | إرفاق `app.py` و`style.css` و`script.js` دون إعادة توليدها |
| واجهات المنتجات | عرض القائمة والتفاصيل، والإضافة والتعديل والحذف والتنقل |
| الطباعة | إخراج الأشجار وجدول الرموز للطرفية ولواجهة المتصفح |
| إعادة التوليد | إعادة تشغيل Java لتحديث HTML بعد تغير بيانات المصدر |

## 12. حل المشاكل الشائعة

| المشكلة | السبب والحل |
|---|---|
| `java` أو `javac` غير معروف | ثبّت JDK 17 واضبط PATH، ثم افتح PowerShell جديدًا |
| فشل `mvnw.cmd` عند أول تشغيل | تحقق من اتصال الإنترنت وتنزيل تبعيات Maven وإعداد Java |
| `Unable to access jarfile` | نفّذ خطوة بناء Java أولًا وتأكد من وجود `compiler-java/target/compiler.jar` |
| `No module named flask` | نفّذ `python -m pip install -r .\sample_project\requirements.txt` في بيئة Python التي تستخدمها للتشغيل |
| صفحة `/output/index.html` غير موجودة | شغّل أمر توليد Java في الخطوة الخامسة أولًا |
| زر فحص المترجم لا يعمل | تحقق من وجود JAR وأن تشغيل `java -version` يعمل في نفس البيئة |
| الأخطاء ليست عشرة في المشروع الخاطئ | اضغط **Restore Original** لإرجاع النسخة المرجعية قبل التجربة |
| `Address already in use` | أوقف نسخة Flask السابقة أو البرنامج الذي يشغل المنفذ 5000 |

> **ملاحظة أكاديمية:** `sample_project_invalid` وأخطاءه جزء من اختبار التحليل الدلالي. عند اكتشاف الأخطاء يجب أن يتوقف توليد HTML، مع الاحتفاظ بتقارير التحليل اللازمة لفهم السبب. كما أن خادم Flask هنا مخصص للعرض والتجربة المحلية، وليس للاستضافة الإنتاجية.
