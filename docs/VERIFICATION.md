# Verification log

Unit tests prove the rules; this file records what was checked against the real world. Newest first.

## 2026-09-26 — RevenueCat Test Store, live: purchase, server entitlement, restore, Customer Center, metadata

Server mode as it ships: `server` with `GEMINI_API_KEY` + `REVENUECAT_SECRET_KEY` + ffmpeg, app built with only
`LECTURELOOP_SERVER_URL` and `REVENUECAT_TEST_STORE_KEY` (no AI key in the APK). Emulator API 35, 1179×2556.

| Check | Result |
|---|---|
| Paywall with the real offering | **Crashed** on the first run: the monthly plan's weekly price divided by 26/6 weeks exactly (`ArithmeticException`, no finite decimal). Fixed with one rounded division + a test (`PlanMath`, 90 tests); the paywall now shows "$19.99 / 6 months · $0.77 a week · Save 33% vs monthly" and "$4.99 / month · $1.15 a week". |
| 3rd lecture of the week → Semester Pass → Test Store sheet "Test valid purchase" | The waiting recording was built straight away; Home shows "Unlimited lectures". No card, no payment: the sheet says it is a test purchase. |
| Server entitlement, live (`GET /v1/subscribers/{id}`) | The server built the 3rd and 4th lecture of the week for the buyer (4 used, allowance 2), which only the live `pro` check allows. REST shows `pro` from `semester_pass`, `store: test_store`, `is_sandbox: true`, $19.99, 30-minute renewal. |
| Account → See plans → buy → back | First pass showed "Free" until reopened; the Account screen now refreshes when it resumes → "Semester Pass · active · Unlimited lectures". |
| Restore purchases (Account) | Stays "Semester Pass · active". |
| Customer Center (Manage subscription) | "Semester Pass · Active · Last charge: $19.99 · Next billing date · Test Store · Restore past purchases". |
| Offering metadata `free_lectures_per_week` 2 → 1 in the dashboard, same APK, fresh install | Home: "1 free lectures left this week"; set back to 2 and confirmed by `GET /v1/subscribers/{id}/offerings`. |

## 2026-09-24 — Server mode: the entitlement and allowance are enforced where the AI key lives

Local server (`./gradlew :server:installDist`, `FREE_LECTURES_PER_WEEK=2`, no RevenueCat secret yet → everyone free).

| Check | Result |
|---|---|
| `POST /v1/cards` with the 1:07 Coulomb lecture | 200, valid card, 6.1 s |
| Same user, 2nd and 3rd request | 200, then **402** `{"error":"weekly_limit","used":2,"limit":2,"resetsAt":"2026-09-27T15:00:00Z"}` returned without an AI call |
| App built with only `LECTURELOOP_SERVER_URL` (no AI key) | `BuildConfig.GEMINI_API_KEY = ""`; the emulator built a card through the server |
| Server allowance 1, app still counting 2 | the app's own count said "1 free lecture left", the server said 402 → the paywall opened with the **server's** numbers ("1/1 free lectures used this week · resets Monday") and the recording kept waiting |
| RevenueCat REST check (`GET /v1/subscribers/{id}`, `pro` active / expired / grace / lifetime / API down) | contract tests; live check done 2026-09-26 (above) |

## 2026-09-24 — Long lecture (74 min): one call vs 10-minute windows

Input: LibriVox public-domain recording of Bertrand Russell, *The Problems of Philosophy*, chapters 1–4 by one reader, joined into one file: 1:13:57, AAC 32 kbps mono, 18.2 MB. Ground truth: an independent Whisper large-v3-turbo transcript (878 segments).

**One call over the whole file (Files API)** — timestamps drift and the quiz leans to the start:

| Model | Time | What went wrong |
|---|---|---|
| gemini-3.5-flash-lite | 21.9 s | "Sense data" placed at 03:38; the term is defined at [09:31] (−5:53). All 5 questions from the first 21 minutes. |
| gemini-3.5-flash | 50.6 s | "Idealism" placed at 56:08; the chapter starts at [58:09] (−2:01). "Descartes' conclusion" at 21:00; "I think, therefore I am" is at [22:17]. |
| gemini-3.5-flash-lite, synthetic 65 min (three demo lectures separated by 20 minutes of silence) | 11.1 s | Coulomb items placed at 23:55–24:40; they are spoken at 63:51–64:58. |

**Windows** (`CardMerger`: 10-minute windows analysed in parallel, timestamps shifted by the window start, items picked across the whole lecture) — server with `ffmpeg -c copy`, 8 windows, 14.3 s:

| Item | Model | Whisper | Δ |
|---|---|---|---|
| Concept: appearance versus reality | 05:01 | [04:56] "Here we have already the beginning of…" (Whisper lost the next 30 s) | ≈ +5 |
| Question: first distinction that causes trouble | 05:00 | same passage | ≈ +4 |
| Question: founder of modern philosophy | 20:24 | [20:22] "Descartes… the founder of modern philosophy" | +2 |
| Concept: method of systematic doubt | 20:33 | inside [20:22–20:36] | 0 |
| Concept: wave motion | 40:02 | [40:02] "light and heat and sound are all due to wave motions" | 0 |
| Question: light, heat and sound | 40:03 | [40:02] | +1 |
| Concept: time order | 50:12 | [50:11] "The time order which events seem to have…" | +1 |
| Question: thunder and lightning | 51:28 | [51:23] "…the thunder and lightning are simultaneous" | +5 |
| Concept: importance of the unknown | 1:10:26 | [70:26] "…everything real is of some importance" | 0 |
| Question: what cannot be known to exist | 1:10:41 | inside [70:32–70:50] | 0 |

Every item lands in the right sentence (max 5 s from the segment start) and the card spans 05:00 → 1:10:41.

**On the phone** (emulator, direct mode, `Mp4AudioSplitter` = MediaExtractor → MediaMuxer, no re-encoding): same file imported from Downloads → card in 48.6 s including the 18 MB copy; items at 02:46, 05:00, 20:30, 20:36, 40:02, 40:03, 50:12, 50:12, 70:03, 70:26 — the same passages as above. One to-do ("Read chapter 3") came from the reader announcing the next chapter; in a real lecture that sentence would not exist, but it shows the to-do rule is literal.

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
| RevenueCat Test Store purchase, restore, Customer Center | Done 2026-09-26 (above). |
| Offering metadata changed in the dashboard (`free_lectures_per_week` 2 → 1) shows up without a new build | Done 2026-09-26 (above). |

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
