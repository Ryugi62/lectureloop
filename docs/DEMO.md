# Demo video — script and recording

Everything in the video is the real app on an Android emulator with real Gemini calls and a real RevenueCat
Test Store purchase. Two things are staged and the captions say so: the three lecture recordings are short original
scripts (`tools/demo/lectures/`) voiced with macOS `say` and fed in as files, and the clock is moved forward one day to
show the review loop. The video has no voice-over and no music; captions carry the story (English).

## Storyboard (target 1:51, hard limit < 2:00)

| # | Scene | s | On screen | Caption |
|---|---|---|---|---|
| 1 | hook | 6 | Home, 2 free lectures left | **LectureLoop** — The forgetting curve starts the moment class ends. Turn the lecture you just heard into a 3-minute review before you leave the room. |
| 2 | record | 7 | Record screen, timer running | **Record in class** — keeps recording with the screen off (microphone foreground service). |
| 3 | import | 12 | Downloads picker → "Signals and Systems" → Build | **...or use any recording** — pick the file and name the class. |
| 4 | processing | 8 | Named steps + real seconds counter (not sped up) | **Checked, then heard** — the server checks the RevenueCat entitlement and the allowance first, then Gemini answers in a strict JSON schema; the app re-checks every rule. |
| 5 | card | 12 | 2 exam hints, "What they said", play from 00:38, key ideas, to-dos | **Only what the lecturer said** — exam hints with their exact words, key ideas, homework with the deadline as spoken. |
| 6 | nextday | 18 | Clock +1 day → Today's loop → quiz 4/5 → "Day 1 done", next review day 3 | **The loop: day 1, 3, 7** — clock moved forward for this demo. |
| 7 | paywall | 22 | 3rd lecture → paywall 2/2 → Semester Pass → Test Store sheet → card is built | **2 free lectures a week** — limit from offering metadata, enforced by the server too; `pro` unlocks; the waiting recording is built straight away. |
| 8 | long | 12 | 74-minute recording → "in 8 parts" → card spanning 05:00 → 1:10 | **Real lectures are 75 minutes** — 10-minute windows heard in parallel keep every timestamp on the right sentence. |
| 9 | account | 8 | Account → Customer Center | **Manage it in the app** — Customer Center for cancel and restore. |
| 10 | end | 6 | End card | Open source (MIT) · github.com/Ryugi62/lectureloop · built by a student whose desktop prototype has turned 27 of their own lectures into review pages this semester. |

The 74-minute recording is LibriVox's public-domain reading of Bertrand Russell's *The Problems of Philosophy* (chapters 1–4); `make_audio.sh` downloads and joins it.

Captions and timings live in `tools/demo/storyboard.json`; `compose.py` writes the same text to an `.srt` file.

## Record it

```bash
# 1. emulator with a store-size screen (width is rounded to 1180; screenshots crop one column)
avdmanager create avd -n lectureloop -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_7
#    in ~/.android/avd/lectureloop.avd/config.ini: hw.lcd.width=1179, hw.lcd.height=2556, hw.lcd.density=440
emulator -avd lectureloop -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &

# 2. the three demo recordings (macOS)
bash tools/demo/make_audio.sh

# 3. server mode (how it ships): start the server with GEMINI_API_KEY + REVENUECAT_SECRET_KEY (README → Run it),
#    local.properties: LECTURELOOP_SERVER_URL=http://10.0.2.2:8787/ and REVENUECAT_TEST_STORE_KEY
#    then record every scene and compose the video
ANDROID_HOME=… FFMPEG=/path/to/ffmpeg bash tools/demo/record_demo.sh
#    → demo/out/LectureLoop-demo.mp4 and LectureLoop-demo.en.srt; fails if the video is 2:00 or longer
```

`drive.py` taps by Compose test tags exposed as resource IDs (`cta`, `import`, `course`, `choice-N`, `plan-semester`,
`due-card`, `icon-Account`, `icon-Close`) and records one clip per scene with `adb shell screenrecord`. Store screenshots:
`bash tools/demo/screenshot.sh docs/screenshots/NN-name.png` (1179×2556, no device frame).
