# نقل وبناء RIO Agent 2.2.2 عبر Termux

1. نزّل ملف `RIOAgent-2.2.2-Termux-Ready.zip` إلى مجلد Download في الهاتف.
2. افتح Termux ونفّذ الأمر الواحد التالي:

```bash
cd /storage/emulated/0/Download/RIOAgent-1.6-upload && unzip -o /storage/emulated/0/Download/RIOAgent-2.2.2-Termux-Ready.zip -d . && bash TRANSFER-TO-TERMUX.sh
```

السكربت يزامن `main` مع GitHub، يضيف ملفات المصدر، ينشئ commit عند وجود تغييرات، ثم يرسلها إلى المستودع. إرسال التغييرات يشغّل workflow البناء تلقائياً.

تابع: https://github.com/aliknanie364-lgtm/RIOAgent/actions

بعد ظهور نجاح `Build RIOAgent APK` افتح التشغيل الأحدث ونزّل artifact باسم `RIOAgent-2.2.2-Stability-Fix-APK`.

مهم: لا يعتبر APK مبنياً إلا بعد ظهور حالة `success` ووجود artifact قابل للتنزيل. هذا المسار يعتمد على صلاحية GitHub التي يستخدمها Termux لدفع التغييرات.
