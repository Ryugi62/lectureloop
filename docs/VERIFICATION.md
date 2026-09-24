# Verification log

Unit tests prove the rules; this file records what was checked against the real world. Newest first.

## 2026-09-24 — Android emulator, end to end (real Gemini, no RevenueCat key yet)

Emulator: API 35 (Google APIs, arm64), 1180×2556 @ 440 dpi. Debug build, `gemini-3.5-flash-lite`.

| Step | Result |
|---|---|
| Launch | Home shows "2 free lectures left this week". |
| Import `DSP lecture 5.m4a` (2:28) from Downloads, class "Signals and Systems" | Card built; the recorded processing clip is 6.4 s from tap to card. 2 exam points, 4 concepts, 5 questions, 2 to-dos. |
| Card | "What they said" unfolds the lecturer's words; the timestamp chip starts playback at that second. |
| Quiz right after class | Counted as practice; the loop does not move (AC-11b). |
| Clock moved +1 day | "Today's loop" shows the lecture at Day 1; 4/5 → "Day 1 done", next review Sunday Sep 27 (day 3). |
| 3rd lecture in the same week | Paywall opens with "2/2 free lectures used this week · resets Monday"; closing it shows "Your recording is waiting". |
| Record screen | Foreground service starts, timer runs, closing discards the audio. |
| RevenueCat Test Store purchase, restore, Customer Center | **Pending** — needs the RevenueCat project key (see REVENUECAT_SETUP.md). |

Bugs found and fixed during this run: a remembered class name was appended to instead of replaced by the demo driver (driver clears the field now); returning from the paywall re-opened the file picker and the paywall (one-shot guards in `CaptureViewModel`); a 147.8 s file was measured as 147 s, which could reject a correct last timestamp (duration now rounds up).

## 2026-09-24 — Gemini, real API (`LiveGeminiTest`)

Input: `tools/demo/lectures/dsp-sampling.txt`, an original 361-word lecture voiced with macOS `say` (2:28, AAC 32 kbps mono, 578 KB). Ground truth: an independent Whisper large-v3-turbo transcript of the same file (45 segments).

| Run | Path | Time | Rule violations |
|---|---|---|---|
| probe (curl-level) | inline | 6.5 s | 0 |
| 1 | inline | 5.0 s | 0 |
| 2 | Files API (upload → poll → generate) | 8.0 s | 0 |
| 3, 4 (after prompt fix) | inline | 5.3 s, 5.4 s | 0 |

Timestamps of run 4 against Whisper (sentence start):

| Item | Model | Whisper | Δ |
|---|---|---|---|
| Exam: sample faster than 2·f_max | 00:38 | [00:38] "please remember this sentence exactly" | 0 |
| Exam: compute the alias frequency | 01:56 | [01:56] "On the exam, I often give you a tone…" | 0 |
| Concept: sampling rate | 00:15 | [00:15] "we measure the signal every t seconds" | 0 |
| Concept: Nyquist rate | 00:35 | [00:34] "2 times f max is called the Nyquist rate" | +1 |
| Concept: aliasing | 00:47 | [00:46] "so what happens if we sample too slowly?" | +1 |
| Concept: anti-aliasing filter | 01:32 | [01:32] "Before the sampler, we put a low-pass filter" | 0 |
| Quiz 1: fs = 1/T | 00:18 | [00:18] | 0 |
| Quiz 2: Nyquist rate | 00:35 | [00:34] | +1 |
| Quiz 3: 7 kHz sampled at 10 kHz | 01:00 | [01:02] "the tone looks exactly like a 3 kHz tone" | −2 |
| Quiz 4: filter before the sampler | 01:34 | [01:36] "It removes everything above half…" | −2 |
| Quiz 5: why 44.1 kHz | 01:47 | [01:47] | 0 |
| To-do: read chapter 4 | 02:09 | [02:08] | +1 |
| To-do: homework 3 | 02:13 | [02:13] | 0 |

Max |Δ| = 2 s (SPEC target ±3 s). The small-talk passage (air conditioner, 01:18–01:27) was left out in every run.

**Found and fixed:** in runs 1–2 the reading ("for next class, read chapter 4") was given the homework's deadline ("next Monday before class"). The prompt now says each task gets only the deadline spoken for that task; runs 3 and 4 give "next class" and "next Monday before class" correctly.

Reproduce: `LIVE_GEMINI=1 GEMINI_API_KEY=… LIVE_AUDIO=demo/audio/DSP\ lecture\ 5.m4a ./gradlew :adapters:gemini:test --tests '*LiveGeminiTest*'` (reports in `adapters/gemini/build/live-gemini-*.txt`).
