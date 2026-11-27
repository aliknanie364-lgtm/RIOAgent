#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle غير مثبت في Termux."
  exit 1
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  for candidate in "$HOME/Android/Sdk" "$PREFIX/opt/android-sdk" "$PREFIX/share/android-sdk"; do
    if [ -d "$candidate" ]; then export ANDROID_HOME="$candidate"; export ANDROID_SDK_ROOT="$candidate"; break; fi
  done
fi
echo "[1/3] فحص ملفات المشروع..."
test -f app/src/main/AndroidManifest.xml
test -f app/src/main/java/com/rioagent/app/MainActivity.java
echo "[2/3] بناء APK..."
gradle --no-daemon clean assembleDebug
echo "[3/3] التحقق والنسخ..."
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
unzip -t "$APK" >/dev/null
OUT="/storage/emulated/0/Download/RIOAgent-2.3.0.apk"
cp "$APK" "$OUT"
echo "SUCCESS: $OUT"
