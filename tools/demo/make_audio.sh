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
# The long-lecture scene uses a public-domain LibriVox recording (Russell, The Problems of Philosophy, ch. 1–4).
if command -v "${FFMPEG:-ffmpeg}" >/dev/null && [ ! -f "$OUT/Philosophy 74 min.m4a" ]; then
  for i in 01 02 03 04; do curl -sSL -o "/tmp/pp$i.mp3" "https://archive.org/download/problems_of_philosophy_librivox/problemsofphilosophy_${i}_russell_64kb.mp3"; done
  "${FFMPEG:-ffmpeg}" -y -loglevel error -i /tmp/pp01.mp3 -i /tmp/pp02.mp3 -i /tmp/pp03.mp3 -i /tmp/pp04.mp3 \
    -filter_complex "[0:a][1:a][2:a][3:a]concat=n=4:v=0:a=1[a]" -map "[a]" -ac 1 -ar 16000 -c:a aac -b:a 32k "$OUT/Philosophy 74 min.m4a"
  rm -f /tmp/pp0?.mp3
fi
ls -la "$OUT"
