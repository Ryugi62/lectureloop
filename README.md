# LectureLoop

**Record the lecture. Leave the room with a 3-minute review. Get quizzed again on day 1, 3 and 7.**

LectureLoop is an Android app for students. Right after class it turns the recording into a review card — what the lecturer said will be on the exam (with the second they said it), the key ideas, and the homework with its deadline as spoken — and then brings a 5-question quiz back on day 1, 3 and 7 until the lecture sticks.

<p>
<img src="docs/screenshots/01-home.png" width="30%" alt="Home: free lectures left, today's loop, your lectures">
<img src="docs/screenshots/02-review-card.png" width="30%" alt="Review card: exam hints with the lecturer's words and a play-from-here timestamp">
<img src="docs/screenshots/03-quiz.png" width="30%" alt="Quiz: one question per screen, the answer links back to the second it was said">
</p>

Built for the RevenueCat Shipaton 2026 **Next Gen Award** (student category). Demo video: _link added on submission_.

## Why

The forgetting curve starts the moment class ends, and the moment class ends is exactly when a student has the least time. I've been running a desktop version of this loop on my own classes this semester: it has turned 27 of my lectures across 5 courses into review pages since September 2026. LectureLoop is that loop rebuilt as a phone app anyone can use in the minute between two classes.

It is deliberately **not an AI summary**. It only keeps what the lecturer actually said, every item carries the timestamp it came from, and the app refuses cards that break its rules.

## How it works

```
 record in class ─┐                         ┌─ rules check (domain) ─ fail ─┐
 (foreground svc) ├─ audio ─ Gemini, 1 call ┤  5 questions, 4 choices,      │ retry once with
 share / import  ─┘   JSON response schema  │  timestamps inside recording  │ the violations
                                            └─ pass ─────────────┐   ◄──────┘
                                                                  ▼
                              review card ── quiz now (practice) ── day 1 ── day 3 ── day 7 ── mastered
                                                                    4/5 moves on · a miss repeats tomorrow
```

- **One multimodal call.** Gemini listens to the audio and must answer in a strict JSON schema (`responseSchema`): exam points with the lecturer's own words as the cue, 3–5 concepts, exactly 5 four-choice questions, to-dos with the deadline copied as spoken, and an `MM:SS` timestamp on every item. Files up to 14 MB go inline; longer lectures use the resumable Files API.
- **The domain decides, not the model.** `ReviewCardRules` rejects a card with the wrong number of questions or choices, an answer index out of range, blank text, or a timestamp past the end of the recording. The use case retries once, sending the exact violations back as feedback. Failed attempts never use the student's free allowance.
- **We never invent exam hints.** If the lecturer gave none, the card says so.
- **The loop.** `ReviewScheduler` brings the quiz back on day 1, 3 and 7. 4 of 5 moves the lecture on, a miss repeats it tomorrow, and a late review keeps the gap instead of bunching reviews together. A quiz taken before it is due is practice and does not move the loop.

## RevenueCat

| What | How |
|---|---|
| Access | One entitlement, `pro`. The app never checks product IDs, so plans can change without an update. |
| Plans | Current offering, packages `$rc_six_month` (**Semester Pass**) and `$rc_monthly`. Prices always come from the store (`StoreProduct.price`); the paywall computes "save 33 % vs monthly" and the weekly price in the domain (`PlanMath`) only when currencies match. |
| Free allowance | 2 lectures a week by default, **overridden remotely** by the offering metadata key `free_lectures_per_week` (clamped 0–14). The paywall headline also comes from metadata (`headline`), so copy and allowance can be tested with Experiments without shipping a build. |
| Paywall | Custom Compose paywall that reports `trackCustomPaywallImpression` (paywall id `lectureloop-weekly-limit`) so conversion shows up in RevenueCat. It opens when the 3rd lecture of the week is captured, **keeps that recording waiting, and builds it straight after the purchase**. |
| Purchase / restore | `awaitPurchase` with cancel treated as a normal outcome, `awaitRestore`, and an `UpdatedCustomerInfoListener` that refreshes the home screen. |
| Manage | RevenueCat **Customer Center** from the Account screen. |
| Environments | Debug builds use the **Test Store** key, release builds the Google Play key (`BuildConfig`, never both). |

**Why a Semester Pass?** A student's need has the shape of a term: it starts in week 1 and peaks at midterms and finals. A monthly plan asks the student to decide again in the middle of exams; a 6-month pass covers one term plus the break before the next one, which is how students budget. Monthly stays as the low-commitment entry.

## Architecture

Clean Architecture, enforced by the build: the inner layers are plain Kotlin/JVM modules, so they *cannot* import Android, RevenueCat or OkHttp.

```
domain/            Timestamp, ReviewCard, ReviewCardRules, ReviewScheduler, QuizGrader, AccessPolicy, PlanMath
application/       ProcessLecture, TodayQueue, SubmitQuiz, AccessStatus, LoadPaywall/Purchase/Restore
                   ports: LectureAnalyzer, LectureRepository, BillingGateway, Clock, IdSource
adapters/gemini/   GeminiLectureAnalyzer (OkHttp + kotlinx.serialization): inline or Files API, response schema
app/               adapters: RevenueCatBilling, JsonLectureRepository, RecordingService, AudioImporter, LecturePlayer
                   infrastructure: AppContainer (composition root) · ui: Jetpack Compose, one question per screen
```

The spec comes first: [`SPEC.md`](SPEC.md) lists the ubiquitous language, the use cases and the acceptance criteria that the tests are named after.

## Tests and verification

- **60 tests**: domain 33, application 19, Gemini adapter 6 (4 contract tests against a mock server + 2 live checks that run only on request), app adapters 2 (JSON round trip, RevenueCat package → plan mapping). `./gradlew check`.
- `LayeringTest` fails the build if domain or application import a framework, SDK or `java.io.File`.
- Physical checks against the real API and an emulator are written up in [`docs/VERIFICATION.md`](docs/VERIFICATION.md): a 2:28 lecture becomes a valid card in about 5–8 s, and every timestamp lands within 2 s of an independent Whisper transcript.

## Run it

Requirements: JDK 21, Android SDK with platform 37, an emulator or phone on Android 8.0+.

1. Create `local.properties` in the project root:
   ```properties
   sdk.dir=/path/to/Android/sdk
   GEMINI_API_KEY=your-gemini-api-key
   REVENUECAT_TEST_STORE_KEY=your-revenuecat-test-store-key
   # release builds only:
   REVENUECAT_GOOGLE_KEY=
   ```
2. In RevenueCat (see [`docs/REVENUECAT_SETUP.md`](docs/REVENUECAT_SETUP.md)): entitlement `pro`; Test Store products for a 6-month Semester Pass and a monthly plan attached to `pro`; offering `default` with packages `$rc_six_month` and `$rc_monthly`; metadata `{"headline": "Keep every lecture in the loop", "free_lectures_per_week": 2}`.
3. `./gradlew installDebug`

Without keys the app still builds and runs: cards cannot be built and the paywall explains which key is missing.

The 2-minute demo is recorded by a script (real app, real API calls): [`docs/DEMO.md`](docs/DEMO.md).

## Privacy

Recordings and cards stay on the phone. When you build a card, that one recording is sent to Google Gemini to be analysed. Subscriptions use RevenueCat's anonymous app user ID. Record only where your school allows it.

## Known limits (next steps)

- This build calls Gemini from the device with a developer key, which is fine for a demo but not for a store release: the next step is a small server that verifies the `pro` entitlement with RevenueCat before spending AI credits.
- Android only; the domain and application layers are plain Kotlin and are ready to move to Kotlin Multiplatform for iOS.
- No accounts or sync yet: cards live in one JSON file per lecture on the device.

## License

[MIT](LICENSE)
