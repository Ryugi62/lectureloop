#!/usr/bin/env bash
# Builds the three demo lectures from the original scripts in tools/demo/lectures (macOS: say + afconvert).
# The audio is only fed to the app on the emulator; it is never part of the published video or repo.
set -euo pipefail
cd "$(dirname "$0")"
OUT="../../demo/audio"; mkdir -p "$OUT"
make() { say -v "${VOICE:-Eddy}" -r 165 -f "lectures/$1.txt" -o "/tmp/$1.aiff"; afconvert -f m4af -d aac -c 1 -b 32000 "/tmp/$1.aiff" "$OUT/$2"; rm "/tmp/$1.aiff"; }
make dsp-sampling "DSP lecture 5.m4a"
make ds-linked-lists "Data Structures - linked lists.m4a"
make em-coulomb "Electromagnetics - Coulomb.m4a"
ls -la "$OUT"
