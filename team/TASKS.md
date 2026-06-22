# Task Board — Automated Mileage Tracker

> Maintained by the Manager. Tasks move down the board; **completed tasks are never deleted**
> (per global no-delete rule) — they move to "Done" with a completion date.
> Each task has a stable id (`T-001`, `T-002`, …) referenced from LOGS.md.
>
> <!-- REDACTED 2026-06-19: the real GCP project id / gcloud config name were replaced below with
> `<redacted-gcp-project-id>` / `<redacted-gcloud-config>` before this repo went public. -->

## Conventions
- **Owner:** which specialist agent is responsible.
- **Phase:** MVP (Android) | Phase-2 (Backend/API) | Phase-3 (iOS) | Phase-4 (Huawei) | Cross-cutting.
- **Blocked-by:** task ids this depends on.
- **Status:** Backlog → Ready → In progress → In review → Done (or Parked).

---

## In Progress

### T-001 · Scaffold Android project — VERIFIED, 2 findings block "Done" (see Backlog entry for full history)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** none
- ✅ verified 2026-06-22 (android-engineer review pass): `./gradlew test` = BUILD SUCCESSFUL, all 5
  unit-test classes pass (debug + release). T-008 signing columns confirmed present in v1 `TripEntity`.
  Package tree / 5 Hilt modules / 7 screens+VMs / DAOs / repositories all conform to the blueprint.
- ⚠️ **HIGH blocker:** `TripTrackingForegroundService` hand-rolls its own start/stop logic and never
  invokes the tested `TripLifecycleStateMachine` (the blueprint's "highest-risk file"). Fix = wire the
  service through the state machine.
- ⚠️ **MEDIUM blocker:** `service/di/ServiceModule.kt` (blueprint §3) was never created — the Hilt
  module that wires the state machine into the service.
- ℹ️ Follow-up (not blocking): WorkManager deps declared but no `Worker` class — revisit with T-007.
- **Next:** fix both blockers (bundled with T-018 logging pass — same files), re-verify, then → Done.

## Ready
_(none yet)_

## Backlog

### T-001 · Scaffold Android project (Kotlin, Compose, Room, Hilt) — SCAFFOLD BUILT (uncommitted; not yet reviewed)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** none
- Set up Gradle, module layout (ui / domain / data / service), Hilt DI graph, Room base.
- ⏳ **2026-06-18 (session 3):** Option A blueprint started — module tree, Room entity/DAO signatures,
  Hilt graph, trip state machine, screen+VM list, build order. Design only, via `android-engineer`.
  Actual scaffolding code comes AFTER, in a fresh full window. See
  `team/SESSION_HANDOFF.md` → "THE NEXT TASK — Option A".
- ✅ done 2026-06-18 (blueprint only — no Kotlin/Gradle code written): full architecture blueprint
  written to `team/blueprints/T-001-android-architecture-blueprint.md`. Covers: single-Gradle-module
  decision (with justification + flip condition), full package tree, `TripEntity`/`TripPhotoEntity`
  schema with the `endTimestamp` sentinel-value decision, `TripDao`/`TripPhotoDao` signatures incl.
  the exact no-duplicate-trip recovery mechanism, Hilt module/binding table, the full trip lifecycle
  state machine (transient pre-trip states vs. the 4 locked `Trip.status` values, plus the resolved
  `pending_ocr` ambiguity), the 7 screens × ViewModel × repository table, a 9-step build order, and
  an explicit android-coder-vs-android-engineer delegation split. 4 open items flagged for other
  specialists (geo-sensors ActivityRecognition detail, ml-ocr OCR client detail, T-008 schema impact
  if signing is added, confirmed zero cloud/cost touch).
- ✅ found 2026-06-19 (this session, board reconciliation — work was not logged when it happened):
  full Gradle project now exists on disk — root `build.gradle.kts`/`settings.gradle.kts`/`gradlew`,
  `app/build.gradle.kts` (namespace `com.mileagetracker.app`, minSdk 29/targetSdk 34, Hilt+KSP+Compose
  plugins, Room `exportSchema` wired), and the full package tree from the blueprint: domain
  (model/statemachine/classification/ocr/export/repository interfaces), data (Room entities/DAOs/
  Converters/repository impls/ML Kit OCR client/CSV writer/5 Hilt modules), service (foreground
  service, ActivityRecognition registrar+receiver, location callback, notification builder+channel),
  ui (7 screens × ViewModel per the blueprint table, nav host, theme). 4 domain unit tests exist
  (`ClassificationRulesTest`, `OdometerTextParserTest`, `CsvExportRulesTest`,
  `TripLifecycleStateMachineTest`). **A debug build succeeded**: `app/build/outputs/apk/debug/app-debug.apk`
  is present (Hilt codegen + KSP + resource/asset merge all completed, incl. ML Kit OCR model assets).
  **Not yet done:** none of this is committed to git (all untracked); it has not been code-reviewed
  against the blueprint or the T-008 signing-column requirement (`signatureBase64`/`signingKeyId` on
  `TripEntity` — unconfirmed whether present); test suite has not actually been *run* (only confirmed
  to exist + compile). **Next:** android-engineer review pass against blueprint + T-008, then run
  `./gradlew test` before calling T-001 Done.

### T-002 · Vehicle detection (ActivityRecognition IN_VEHICLE + thresholds)
- **Owner:** android-engineer + geo-sensors-specialist · **Phase:** MVP · **Blocked-by:** T-001
- Transition API, 70% start confidence, 30s silent retry, foreground service lifecycle.

### T-003 · Trip classification flow (Work/Private + mandatory business reason)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** T-001
- HIGH-importance notification channel `mileage_tracker_trip_alerts`, lock-screen action, pending states.

### T-004 · GPS distance tracking + trip-end detection
- **Owner:** geo-sensors-specialist · **Phase:** MVP · **Blocked-by:** T-002
- Fused location, 10m distance filter, Haversine accumulation, 3-min inactivity stop rule, battery review.

### T-005 · Odometer OCR capture (CameraX + ML Kit) + manual fallback
- **Owner:** ml-ocr-specialist + android-engineer · **Phase:** MVP · **Blocked-by:** T-001
- `\b\d{5,6}\b` parsing, 80% confidence gate, photo-retention setting, manual entry fallback.

### T-006 · Local persistence + recovery (Room schema, no duplicate trips)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** T-001
- Trip entity per handoff brief §6, restore active/pending trip on restart.

### T-007 · CSV export to Downloads (fixed column order, completed trips only)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** T-006
- `mileage_trips_YYYYMMDD_HHMMSS.csv`, UTF-8, exclude pending Work trips.

### T-008 · Cryptographic trip signing (tamper-evident logbook) — DECIDED (design only, implementation rides with T-006)
- **Owner:** security-crypto-specialist · **Phase:** Cross-cutting · **Blocked-by:** T-006
- Decide signing scheme (per-trip hash chain vs. signature), key storage (Keystore), SARS audit value.
- ✅ done 2026-06-18 (session 3, `/team-debate`, 2 rounds + cost ruling): **per-trip ECDSA P-256
  signature** (Android Keystore, StrongBox/TEE) + a **rolling tail-hash chain** held in a one-row
  DataStore entry (not a per-row column), folded into each trip's signed payload, advancing in
  finalization order. Exact schema impact: two new nullable `TripEntity` columns —
  `signatureBase64`, `signingKeyId` — one additive migration, no `previousTripHash` column. Full
  canonical-field list, write-order/durability resolution, and the documented tail-truncation
  limitation are in the `[2026-06-18 17:10] DECISION` entry in `team/LOGS.md`. Cost ruling:
  **APPROVE** — $0 MVP impact, Phase-2 addition is noise inside T-010's existing projections.
  `team/blueprints/T-001-android-architecture-blueprint.md` open-question §3 updated to match.
- ⏳ **note for T-006:** build the two signing columns + migration in from the start per this
  decision — do not build the bare schema first and retrofit.

### T-009 · Backend sync API (Flask on Cloud Run + Firestore)
- **Owner:** backend-engineer · **Phase:** Phase-2 · **Blocked-by:** T-006, T-008
- Idempotent upload endpoint, Firestore security rules, GCP Storage for photos.

### T-010 · Cost model for backend (Cloud Run / Firestore / Storage / ML) — DONE (ruling delivered)
- **Owner:** cost-architect · **Phase:** Phase-2 · **Blocked-by:** none
- Per-user/month cost projection at 20, 1k, 10k users; recommend free-tier-safe defaults.
- GCP project exists: `<redacted-gcp-project-id>` (gcloud config `<redacted-gcloud-config>`), billing NOT linked.
  This cost model gates the billing-link decision — do it before enabling spend.
- ✅ done 2026-06-18 (session 3): **ruling = APPROVE-WITH-CHANGES.** Full report written to
  `team/blueprints/T-010-backend-cost-model.md`. Headline: ~$0/mo at 20 users, ~$2-4/mo at 1k,
  ~$50-95/mo at 10k (~$0.005-0.01/user/mo) — **provided** 5 required changes ship: batched
  Firestore writes (not per-trip), photo upload OFF-by-default + compressed + lifecycle rules,
  Cloud Run `min-instances=0`, OCR stays on-device (Cloud Vision would cost ~$2,700/mo at 10k —
  rejected), and billing-link ships with a budget+alerts+**Pub/Sub→Cloud Function kill-switch**
  (not alerts alone). Binding free-tier constraint identified: Firestore 20k writes/day, hit at
  ~3.3k users (per-trip) / ~20k users (batched). All projections are session-1 assumptions
  (6 trips/user/day, 15% photo opt-in, 100% active ratio) — re-run with real telemetry before
  scaling past closed testing.

### T-011 · Analytics events + SARS-ready reporting
- **Owner:** analytics-specialist · **Phase:** Phase-2 · **Blocked-by:** T-009
- Event taxonomy, dashboard metrics, SARS export workflow (approved+completed only).

### T-012 · Store publishing readiness (Play / App Store / Huawei)
- **Owner:** compliance-qa-specialist · **Phase:** Cross-cutting · **Blocked-by:** T-004, T-005
- Prominent disclosure, privacy policy, data-safety forms, 20-tester rule, Sign in with Apple.

### T-013 · iOS port (Swift, CoreMotion, CoreLocation, Vision)
- **Owner:** ios-engineer · **Phase:** Phase-3 · **Blocked-by:** stable MVP + backend contract
- Mirror MVP behaviour; Sign in with Apple; HMS/Huawei flavor considerations tracked separately.

### T-014 · Repo, Docker & CI setup (GitHub + .venv + Cloud Run image) — IN PROGRESS
- **Owner:** devops-engineer · **Phase:** Cross-cutting · **Blocked-by:** none
- Git repo + `.gitignore` (ignore `.venv/`, secrets, keystores, `google-services.json`, `serviceAccount*.json`),
  backend Dockerfile + docker-compose (with Firestore emulator), Python `.venv` workflow + pinned deps,
  GitHub Actions CI (lint/test/build/container).
- ✅ done 2026-06-18: `.gitignore` + `git init`; `backend/` (Flask placeholder, pinned `requirements.txt`,
  multi-stage non-root Dockerfile, pytest, `ruff.toml`, README .venv workflow); root `docker-compose.yml`
  (backend + Firestore emulator); `.github/workflows/ci.yml`. Validated locally: venv install OK, ruff clean, pytest 3/3.
- ⏳ open: real `docker build` verification (Docker engine wasn't running locally — CI build-image job covers it);
  create GitHub remote + first push.

### T-016 · Huawei HMS technical adaptation (no GMS on Huawei devices)
- **Owner:** geo-sensors-specialist + ml-ocr-specialist (+ backend-engineer) · **Phase:** Phase-4 · **Blocked-by:** T-002, T-005, T-009
- Swap Google APIs for Huawei equivalents per `publisheing guide.md` §7.1: Huawei Account Kit
  (replaces Google OAuth), Huawei Location Kit incl. `ActivityIdentificationService` (replaces
  FusedLocation/ActivityRecognition), Huawei ML Kit Text Recognition (replaces ML Kit OCR). Direct
  client Firestore access requires GMS, so Huawei builds must route sync through the Flask backend
  (T-009) over plain HTTPS REST instead of the Firestore SDK.
- Added 2026-06-19 — this task didn't exist before today; `compliance-qa-specialist`'s T-012 covers
  publishing *readiness/compliance* for all 3 stores, but the underlying *technical* HMS flavor swap
  had no owner. Split out so the right specialists pick it up.

### T-017 · Compose UI screens — layout, navigation, visual design (all 7 screens)
- **Owner:** android-engineer (+ a11y-architect for accessibility pass) · **Phase:** MVP · **Blocked-by:** T-001
- Added 2026-06-19 — every other MVP task (T-002–T-007) mentions its screen in passing (e.g. "shows
  a numeric text field"), but none of them own the actual UI build: layout, Material3 components,
  navigation graph, and the loading/empty/error/limited-mode visual states for each of the 7 screens
  in `developer_handoff_brief.md` §7. This task is that missing, explicit front-end work.
- The 7 screens: Setup/Permissions, Home/Status, Trip Classification, Odometer Capture, Trip
  History, Export, Settings — composables + ViewModel state contracts already named in
  `team/blueprints/T-001-android-architecture-blueprint.md` §5; this task is building their actual
  visual layout and navigation, not their state-holding logic (that's owned by T-002/T-003/T-005/
  T-007's ViewModel-layer steps).
- Navigation graph wiring (`NavHost`, the 7 routes, deep-link handling for the lock-screen
  notification action from T-003.3) and the shared Compose theme/typography/color scheme are also
  this task's responsibility, not a side effect of any single screen.

### T-018 · Field-debuggability — structured logging + error-handling boundaries — ✅ DONE 2026-06-22
- **Owner:** android-engineer (design) + android-coder (mechanical grind) · **Phase:** MVP/Cross-cutting · **Blocked-by:** T-001
- ✅ done 2026-06-22: Timber 5.0.1 + `FileLoggingTree` (rotating on-device log, planted in Application);
  `DebugLogFileProvider` + Settings "Export debug log" button; `MT-*` tagged try/catch on service/
  location/OCR/CSV/Room surfaces; OCR-traps-trip bug fixed. android-engineer designed + did the
  judgment parts, android-coder (Haiku) did the grind, android-engineer reviewed. `./gradlew test`
  green. Remaining `MT-ActivityRecognition` logging rides with T-002 (its registration call doesn't
  exist yet). Review found one HIGH issue (main-thread I/O) → split out as T-019, now also done.
- Added 2026-06-22 — user requirement for field testing: a tester hands back a log file, the Manager
  diagnoses from it. The android-engineer review (2026-06-22) confirmed the app currently has **zero
  logging and zero try/catch at any I/O boundary** — a field failure today looks identical to the app
  silently doing nothing.
- Scope: (1) add Timber + a custom file-writing `Tree` (append-only, size-rotated under `filesDir`,
  $0/on-device, no network); (2) an "Export debug log" action on `SettingsScreen`/`SettingsViewModel`
  reusing `CsvFileWriter`'s MediaStore share pattern; (3) try/catch at the 7 named crash-prone surfaces
  — foreground service `onStartCommand`/`onDestroy`, ActivityRecognition (un)register, location callback,
  ML Kit OCR client, CSV writer, Room write paths — each logging via greppable `MT-*` tags
  (`MT-Service`, `MT-Location`, `MT-OCR`, `MT-Export`, `MT-Repository`, `MT-ActivityRecognition`).
- ⚠️ Folds in a real correctness fix: OCR exceptions must degrade to `NoTextFound`, never propagate —
  today an uncaught OCR error could trap a trip in `pending_ocr`, violating "trip must always save."
- **Cost:** $0 — on-device only, no cloud log sink, no `cost-architect` ruling needed.
- **Plan:** bundle with the T-001 blocker fix (state-machine wiring + ServiceModule) — both edit the
  same files (service, OCR client, CSV writer, repositories), so do them in one pass to avoid touching
  those files twice. Full per-surface detail in the 2026-06-22 android-engineer review.

### T-019 · Move file/MediaStore I/O off the main thread — ✅ DONE 2026-06-22
- **Owner:** android-coder (per android-engineer review finding) · **Phase:** MVP · **Blocked-by:** T-018
- Found by the T-018 review: both `CsvFileWriter.writeToDownloads` (core CSV export) and
  `DebugLogFileProvider.exportDebugLogToDownloads` were called with blocking disk/MediaStore I/O
  directly inside `viewModelScope.launch` (main thread) — a UI-freeze/ANR risk on slow devices.
  Inherited from the original CSV-export reference, not a coder mistake.
- ✅ done 2026-06-22: wrapped both calls in `withContext(Dispatchers.IO)` from their ViewModels
  (`ExportViewModel`, `SettingsViewModel`). `./gradlew test` green. No error-handling/Cancellation
  behavior changed.

### T-015 · Emulator-based GPS-route test harness (Android) — IN PROGRESS
- **Owner:** compliance-qa-specialist + geo-sensors-specialist · **Phase:** Cross-cutting · **Blocked-by:** T-001 (for app under test)
- Use the Android emulator to test trip detection without driving: replay GPS routes (GPX/KML or
  scripted `adb emu geo fix` coordinate streams) to exercise trip-start, distance accumulation, and
  the false-stop logic (traffic-light pause vs. real 3-min stop). Define repeatable route fixtures.
- ✅ done 2026-06-18: `ANDROID_HOME`/`ANDROID_SDK_ROOT` set to `C:\Android\Sdk` (User scope); `adb`+`emulator` on PATH.
- ✅ done 2026-06-18: `test_device` boots — Android 14 (API 34), `emulator-5554`, ~35s; `adb emu geo fix` GPS injection verified OK.
  <!-- earlier "blocked" note: wrong SDK path (%LOCALAPPDATA%\Android\Sdk was empty); real SDK is C:\Android\Sdk -->
  ~~⛔ blocked: AVD cannot boot — system image not installed / no cmdline-tools~~ (resolved: it was a wrong SDK path, not a missing image).
- ⏳ open: author route fixtures (normal trip, stop-start traffic, park-and-stop); wire into
  `compliance-qa-specialist` test scenarios (brief §10 #1, #2); needs the app from T-001 to drive against.

---

## Done
_(none yet — completed tasks move here with a `✅ done YYYY-MM-DD` tag)_
