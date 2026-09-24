# LectureLoop — SPEC v0.2 (2026-09-24)

## 0. One line
A student records (or shares) a lecture right after class and, before leaving the room, gets a **review card**: the points the lecturer said will be tested (each with the second it was said), a 5-question quiz, and the homework that was assigned. The quiz then comes back on day 1, 3 and 7 until it is mastered — the **loop**.

The essence is not "an AI summary". It is **breaking the forgetting curve right after class**, using only what the lecturer actually said.

## 1. Success conditions (numbers) · non-goals
- `./gradlew check` green: domain + application + Gemini adapter unit tests, zero network.
- Layering is enforced by the build: `:domain` and `:application` are plain Kotlin/JVM modules with no Android, RevenueCat, OkHttp or file-system dependency (they cannot compile against them). `LayeringTest` also scans imports.
- Physical check (recorded in `docs/VERIFICATION.md`):
  - A 2:28 lecture → valid review card in ≤ 30 s, every timestamp within ±3 s of an independent Whisper transcript.
  - Android emulator: record/import → card → quiz → 3rd lecture of the week shows the paywall → RevenueCat Test Store purchase → `pro` entitlement active → processing unlocked → restore works.
- Demo video < 2:00.
- Long lectures: a 74-minute recording → every item within the right sentence (≤ 15 s of the Whisper segment start) and questions spread over the whole lecture.
- Server mode: no AI key in the app; the weekly allowance and `pro` are enforced by the server before any AI call.
- Non-goals (v0.2): on-device transcription, accounts/sync, sharing cards with classmates, iOS build, Play Store release.

## 2. Constraints
- Kotlin 2.4 (AGP 9 built-in Kotlin), Jetpack Compose (Material 3), minSdk 26, targetSdk 36, compileSdk 37 (required by Compose BOM 2026.09).
- RevenueCat `purchases` + `purchases-ui` 10.22.x. Debug builds use the **Test Store** key; release builds must use the Play key (`BuildConfig` switch).
- Gemini REST (`generateContent`) with a JSON response schema. Inline audio ≤ 14 MB, Files API above.
- Keys live in `local.properties` → `BuildConfig`; never committed.
- Privacy: audio leaves the phone only for analysis; the app says so on the record screen. Record only where your school allows it.

## 3. Ubiquitous language (code names are identical)
| Term | Meaning | Code |
|---|---|---|
| Lecture | One class session the student captured | `Lecture` |
| Recording | The audio file of a lecture and its length | `AudioRef` |
| Review card | What the student studies after class | `ReviewCard` |
| Timestamp | Second in the recording where the supporting sentence starts | `Timestamp` |
| Exam point | Something the lecturer signalled will be tested, with the lecturer's own words | `ExamPoint(point, cue, at)` |
| Concept | One core idea explained in 1–2 plain sentences | `Concept` |
| Quiz item | 4-choice question with answer and explanation | `QuizItem` |
| To-do | Reading/homework the lecturer assigned, deadline as spoken | `Todo` |
| Card rule violation | Why a generated card is rejected | `Violation` |
| Review step | Day 1, 3, 7 after the lecture | `ReviewStep` |
| Review state | Current step, due time, attempts; `step == null` means mastered | `ReviewState` |
| Weekly allowance | Free lectures per week (default 2, remote via offering metadata) | `AccessPolicy`, `Access` |
| Pro | RevenueCat entitlement that unlocks unlimited lectures | `ENTITLEMENT_PRO = "pro"` |
| Plan | A purchasable package shown on the paywall | `PlanOption`, `PlanKind` |
| Semester Pass | 6-month subscription — one academic term | `PlanKind.SEMESTER` |

## 4. Domain model
- Aggregate root `Lecture(id, course, recordedAt, audio, card, review)` (the title lives in the card).
- Value objects: `Timestamp`, `ReviewCard`, `ExamPoint`, `Concept`, `QuizItem`, `Todo`, `ReviewState`, `PlanOption`.
- Domain services (pure functions): `ReviewCardRules.check`, `ReviewScheduler`, `QuizGrader`, `AccessPolicy`, `PlanMath`.

## 5. Use cases (application)
| UC | Input | Output | Rules |
|---|---|---|---|
| UC-1 `ProcessLecture` | course, audio | `Paywalled` · `Done(lecture)` · `Failed(reason)` | Allowance first (no AI call when paywalled). Card is checked; on violations or a malformed answer the analyzer is called **once more with the violations as feedback**. Failed attempts do not use the allowance. |
| UC-2 `TodayQueue` | now | lectures due for review, most overdue first | mastered lectures never appear |
| UC-3 `SubmitQuiz` | lecture id, answers | score, next review | pass = ≥ 4/5 → next step; fail → same step tomorrow |
| UC-4 `LoadPaywall` / `Purchase` / `Restore` | plan id | `Unlocked` · `Cancelled` · `Failed` | plans ordered Semester first; savings vs monthly computed in the domain; the paywall impression is reported; a recording that hit the paywall is built right after `Unlocked` |
| UC-5 `AccessStatus` | — | pro?, lectures left this week, reset time | offering metadata `free_lectures_per_week` overrides the default 2 |
| UC-6 `BuildCard` | audio | `Built(card)` · `Rejected(reason)` | recordings longer than one 10-minute window are cut (`AudioSplitter`), analysed in parallel, shifted and merged (`CardMerger`); title/summary from `CardComposer` (text only) |
| UC-7 server `CardService` | RevenueCat app user ID, time zone, audio | card · 402 limit · 422 · 429 · 502 | `pro` via RevenueCat REST (secret key) → weekly allowance per user → `BuildCard`; only built cards count |

## 6. Acceptance criteria (Given / When / Then → test)
| AC | Given / When / Then | Test |
|---|---|---|
| AC-1 | "02:08", "1:02:08", "7", "-1:00", "ab" / `Timestamp.parse` / 128 s, 3728 s, null, null, null; label round-trips | `TimestampTest` |
| AC-2 | a card with 4 quiz items, 2 concepts, 3 choices, answer index 4, blank cue, timestamp past the end / `ReviewCardRules.check` / one violation per broken rule; a valid card has none | `ReviewCardRulesTest` |
| AC-3 | lecture processed Mon 10:00 / `ReviewScheduler` / due Tue 10:00; pass → Thu 10:00; pass → next Mon 10:00; pass → mastered; fail → +1 day same step; late pass keeps the gap | `ReviewSchedulerTest` |
| AC-4 | 5 answers / `QuizGrader.grade` / correct count; unanswered counts wrong | `QuizGraderTest` |
| AC-4b | analyzer answers / `ProcessLecture` / phases reported in order: allowance → listening (1) → checking → listening (2) → checking → saving | `ProcessLectureTest` |
| AC-5 | free user, 2 lectures this ISO week (Asia/Seoul) / `AccessPolicy.decide` / `Paywalled(used 2, limit 2, resets next Monday 00:00 local)`; pro → `Granted(unlimited)`; week boundary respected | `AccessPolicyTest` |
| AC-6 | monthly $4.99, semester $19.99 / `PlanMath` / semester first, saves 33 % vs 6 × monthly, weekly price $0.77 | `PlanMathTest` |
| AC-7 | fake analyzer returns an invalid card then a valid one / `ProcessLecture` / second call receives the violations, result `Done`, 2 calls | `ProcessLectureTest` |
| AC-8 | paywalled user / `ProcessLecture` / `Paywalled`, analyzer never called | same |
| AC-9 | analyzer fails twice / `ProcessLecture` / `Failed`, nothing saved, allowance unchanged | same |
| AC-10 | lectures mastered / due 3 days ago / due yesterday / due tomorrow / `TodayQueue` / only the due ones, most overdue first | `ReviewUseCasesTest` |
| AC-11 | `SubmitQuiz` with 4/5 on a due lecture / next step saved | `ReviewUseCasesTest` |
| AC-11b | quiz taken before it is due (e.g. right after class) / `SubmitQuiz` / scored as practice, review state unchanged | `ReviewUseCasesTest` |
| AC-12 | Gemini inline request / adapter / POST `models/{model}:generateContent`, `x-goog-api-key` header, `inline_data` audio, response schema forces exactly 5 quiz items; the JSON answer maps to a `ReviewCard` | `GeminiLectureAnalyzerTest` |
| AC-13 | audio > 14 MB / adapter / resumable upload start → upload+finalize → `file_data.file_uri` in generateContent | same |
| AC-14 | HTTP 429 / adapter / `AnalyzerException.RateLimited`; non-JSON body → `Malformed` | same |
| AC-15 | retry with violations / adapter / prompt contains each violation message | same |
| AC-16 | source tree / `LayeringTest` / no `android.`, `com.revenuecat`, `okhttp3`, `java.io.File` import in domain/application | `LayeringTest` |
| AC-17 | 148 s, 1290 s, 1500 s recordings / `CardMerger.windows` / 1, 2 (90 s tail joins), 3 windows | `CardMergerTest` |
| AC-18 | window cards / `shiftedBy` + `CardMerger.merge` / every timestamp moved by the window start; 5 questions and ≤ 5 concepts picked across windows (evenly spaced when windows > slots), shown in lecture order; ≤ 8 exam points; duplicate to-dos collapse | `CardMergerTest` |
| AC-19 | 25-minute recording + splitter / `BuildCard` / 3 windows analysed, shifted, merged, splitter released; a window failing twice fails the card; an unsplittable format uses one call | `WindowedBuildCardTest` |
| AC-20 | free user at the server / `CardService` / 2 cards a week then `Limit` without an AI call; per user; `pro` unlimited; failed cards not counted; Monday reset in the student's zone; usage survives restart | `CardServiceTest` |
| AC-21 | RevenueCat REST `subscribers` answer / `RevenueCatRestEntitlements` / `pro` active, expired, grace period, lifetime, missing; API error → free (fail closed); app user ID URL-encoded | `RevenueCatRestEntitlementsTest` |
| AC-22 | HTTP `POST /v1/cards` / `HttpApi` / 200 card JSON, 402 with used/limit/resetsAt, 400 without user or seconds | `HttpApiTest` |
| AC-23 | server answers 402 / `ProxyLectureAnalyzer` + `ProcessLecture` / `Paywalled` with the server's numbers, not retried | `ProxyLectureAnalyzerTest`, `ProcessLectureTest` |
| AC-24 | Files API upload / `GeminiLectureAnalyzer` / the uploaded file is deleted after the call | `GeminiLectureAnalyzerTest` |

## 7. Architecture (Clean) — dependencies point inward only
```
domain/            pure Kotlin: model, rules, scheduler, access policy, plan math
application/       use cases + ports (LectureAnalyzer, LectureRepository, BillingGateway, Clock, IdSource)
adapters/gemini/   JVM: GeminiLectureAnalyzer, GeminiCardComposer, ProxyLectureAnalyzer, CardJson (OkHttp + kotlinx.serialization)
server/            JVM: CardService, RevenueCatRestEntitlements, FileUsageStore, FfmpegAudioSplitter, HttpApi (JDK HttpServer)
app/               Android: adapters (RevenueCat billing, JSON file repository, recorder, player),
                   infrastructure (AppContainer = composition root, BuildConfig keys), Compose UI
```

## 8. UI acceptance (Toss-style checklist, adapted to Android)
1. Phone first: every screen works at 1179×2556 and 1080×2400 with no clipped text.
2. One question per screen: the quiz shows one item at a time with a progress bar.
3. Type scale: titles ≥ 22 sp bold, body 16 sp, captions 13 sp — three levels.
4. Spacing ≥ 24 dp between sections, cards 20 dp radius, no stacked shadows.
5. One primary action per screen, full-width, bottom, ≥ 56 dp.
6. Numbers first: result screens lead with the number (score "4/5", "2 lectures left", "Day 3").
7. Evidence folded: the lecturer's quote behind a tap on the exam point; the timestamp chip plays the audio from that second.
8. Microcopy: short and friendly; no jargon without a one-line gloss.
9. One brand blue (#3182F6) + ok/warn/error; body contrast ≥ 4.5:1; dark theme.
10. No remote fonts; loading uses skeletons and named steps, not a bare spinner.

## 9. Pricing rationale (why a Semester Pass)
A student's need has the shape of a term: it starts in week 1 and peaks at midterms and finals. A monthly plan asks the student to decide again in the middle of exams; a Semester Pass (6 months — one term plus the break before the next) matches how students budget. The monthly plan stays as the low-commitment entry. The free allowance (2 lectures a week) is enough to try the loop on one course and is controlled remotely through offering metadata, so it can be tuned without an app update.

## 10. Change log
- v0.2 2026-09-24 — measured timestamp drift on a 74-minute recording → 10-minute windows (AC-17..19); server mode with RevenueCat REST entitlement check and server-side allowance (AC-20..23); Files API uploads deleted after use (AC-24).
- v0.1 2026-09-24 — first version. Emulator run added AC-11b (practice quiz) and the paywall keeps the waiting recording (UC-4 builds it after purchase).
