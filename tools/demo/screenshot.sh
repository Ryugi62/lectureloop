#!/usr/bin/env bash
# Saves the current emulator screen as a store-size screenshot: 1179x2556, no device frame.
# The 1179-wide AVD reports 1180 px (width is rounded to even), so one column is cropped.
#   bash tools/demo/screenshot.sh docs/screenshots/01-home.png
set -euo pipefail
ADB="${ANDROID_HOME:?set ANDROID_HOME}/platform-tools/adb"
out="$1"; tmp="$(mktemp -t llshot).png"
"$ADB" exec-out screencap -p > "$tmp"
sips --cropToHeightWidth 2556 1179 "$tmp" --out "$out" >/dev/null
sips -g pixelWidth -g pixelHeight "$out" | tail -2
rm -f "$tmp"
