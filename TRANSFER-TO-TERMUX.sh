#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_URL="https://github.com/aliknanie364-lgtm/RIOAgent"

if [ ! -d .git ]; then
  echo "ERROR: نفّذ هذا السكربت داخل مجلد المشروع الذي يحتوي على .git"
  exit 1
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "ERROR: لم يتم العثور على remote باسم origin"
  exit 1
fi

BRANCH="$(git branch --show-current)"
if [ "$BRANCH" != "main" ]; then
  echo "الفرع الحالي: $BRANCH — التحويل إلى main"
  git checkout main
fi

echo "[1/4] مزامنة آخر تغييرات GitHub..."
git pull --rebase origin main

echo "[2/4] تجهيز ملفات RIO Agent 2.2.2..."
git add -A

if git diff --cached --quiet; then
  echo "لا توجد تغييرات جديدة لإرسالها."
else
  echo "[3/4] إنشاء commit..."
  git -c user.name="RIOAgent" -c user.email="rioagent@example.com" commit -m "Update RIOAgent 2.2.2 adaptive control"
fi

echo "[4/4] إرسال الملفات وتشغيل بناء APK على GitHub Actions..."
git push origin main

echo ""
echo "تم إرسال المصدر. تابع بناء APK من هنا:"
echo "$REPO_URL/actions"
echo "بعد نجاح Build RIOAgent APK نزّل artifact باسم RIOAgent-2.2.2-Stability-Fix-APK."
