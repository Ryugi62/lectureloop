#!/usr/bin/env bash
# One command from a clean emulator to the finished demo video.
#   ANDROID_HOME=... FFMPEG=... bash tools/demo/record_demo.sh
# Prereqs: emulator running (1179x2556 AVD, see docs/DEMO.md), local.properties with GEMINI_API_KEY and
# REVENUECAT_TEST_STORE_KEY, and the demo recordings built by tools/demo/make_audio.sh.
set -euo pipefail
cd "$(dirname "$0")/../.."
ADB="${ANDROID_HOME:?set ANDROID_HOME}/platform-tools/adb"
PKG=io.github.ryugi62.lectureloop
AUDIO="${DEMO_AUDIO_DIR:-demo/audio}"

grep -q '^REVENUECAT_TEST_STORE_KEY=.\+' local.properties || { echo "REVENUECAT_TEST_STORE_KEY missing in local.properties"; exit 2; }
grep -q '^GEMINI_API_KEY=.\+' local.properties || { echo "GEMINI_API_KEY missing in local.properties"; exit 2; }
"$ADB" get-state >/dev/null

./gradlew -q :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB" shell pm clear "$PKG" >/dev/null
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO
"$ADB" shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

# Real clock back on (a previous run moves it forward one day), clean status bar.
"$ADB" root >/dev/null 2>&1 || true; sleep 2
"$ADB" shell settings put global auto_time 1 || true
"$ADB" shell settings put global sysui_demo_allowed 1
"$ADB" shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
"$ADB" shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1015 >/dev/null
"$ADB" shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
"$ADB" shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null

"$ADB" shell rm -f "/sdcard/Download/*.m4a"
for f in "$AUDIO"/*.m4a; do "$ADB" push "$f" "/sdcard/Download/$(basename "$f")" >/dev/null; done
"$ADB" shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || \
  "$ADB" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download >/dev/null

rm -rf demo/raw
python3 tools/demo/drive.py --out demo/raw
python3 tools/demo/compose.py --raw demo/raw --out demo/out
"$ADB" shell settings put global auto_time 1 || true
