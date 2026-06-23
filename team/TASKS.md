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

### T-001 · Scaffold Android project — ✅ DONE 2026-06-22 (both blockers resolved + re-verified green) — card relocated to Done below
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** none
- ✅ verified 2026-06-22 (android-engineer review pass): `./gradlew test` = BUILD SUCCESSFUL, all 5
  unit-test classes pass (debug + release). T-008 signing columns confirmed present in v1 `TripEntity`.
  Package tree / 5 Hilt modules / 7 screens+VMs / DAOs / repositories all conform to the blueprint.
- ✅ resolved 2026-06-22 (android-engineer fix-verify pass): both blockers below were ALREADY fixed in
  commit `c846b05` (the T-018 logging pass — same files, exactly as the plan predicted). Re-confirmed by
  reading the actual sources + a clean `./gradlew clean test` → BUILD SUCCESSFUL in 1m42s, 62 tasks, all
  5 unit-test classes green on BOTH debug and release. No new source changes were needed (working tree clean).
  - Service now flows through the state machine: `TripTrackingForegroundService` `@Inject`s
    `TripLifecycleStateMachine`; start → `onStartEvent` / `resolvePromptPendingIntoActiveTrip`; all 3 stop
    paths (manual, 3-min ConfirmedInactivity, 2-min UnstableSignalTimeout) → `handleStopEvent` →
    `onStopEvent`; no hardcoded `TripStatus` literals. A `tripLifecycleMutex` serializes restart-recovery
    against start/stop. Locked thresholds (3min/2min/10m) intact.
  - `service/di/ServiceModule.kt` exists per blueprint §3: `@InstallIn(ServiceComponent::class)`,
    `@Provides @ServiceScoped` for the concrete `TripLifecycleStateMachine` (correct idiom — no interface to `@Binds`).
- ~~⚠️ **HIGH blocker:** `TripTrackingForegroundService` hand-rolls its own start/stop logic and never
  invokes the tested `TripLifecycleStateMachine` (the blueprint's "highest-risk file"). Fix = wire the
  service through the state machine.~~ → **RESOLVED** (see above; already fixed in `c846b05`).
- ~~⚠️ **MEDIUM blocker:** `service/di/ServiceModule.kt` (blueprint §3) was never created — the Hilt
  module that wires the state machine into the service.~~ → **RESOLVED** (see above; file exists in `c846b05`).
- ℹ️ Follow-up (not blocking): WorkManager deps declared but no `Worker` class — revisit with T-007.
- ℹ️ Deferred to T-002 (not blocking): ActivityRecognition auto-start is registered but not yet feeding
  `TripStartEvent.ConfidentVehicleEntry` into the state machine (tagged TODO in the service class doc).
- ~~**Next:** fix both blockers (bundled with T-018 logging pass — same files), re-verify, then → Done.~~ → **DONE.**

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

### T-002 · Vehicle detection (ActivityRecognition IN_VEHICLE + thresholds) — ~~IN PROGRESS (detection spec delivered)~~ DONE 2026-06-23 (detection pipeline + T-002.1 Setup/Permissions screen both complete; T-002.5 Bluetooth extended scope remains deliberately parked/deferred, not MVP)
- **Owner:** android-engineer + geo-sensors-specialist · **Phase:** MVP · **Blocked-by:** ~~T-001~~ (unblocked — T-001 Done)
- Transition API, 70% start confidence, 30s silent retry, foreground service lifecycle.
- ⏳ 2026-06-22 (geo-sensors-specialist DESIGN pass): detection spec written to
  `team/blueprints/T-002-vehicle-detection-spec.md`. Gap analysis: **T-002.2 (domain start-event types)
  already exists** (`TripStartEvent` sealed type + `onStartEvent` handle all 3 cases — only 2 missing tests);
  **T-002.3** shells exist (`ActivityRecognitionRegistrar`, `ActivityTransitionReceiver`) but bodies are
  `TODO`; **T-002.4 confidence-acquisition window is net-new** (the real work). Decision: new
  `ConfidenceAcquisitionWindow` — 5s `requestActivityUpdates` subscription, tracks running MAX confidence,
  30s injected-`CoroutineScope` timer (TestScope-driveable), unregisters BEFORE emitting on both paths
  (battery-leak discipline). Spec includes exact TDD cases + a wiring checklist for the android side.
- ~~**Next:** android-engineer (+ android-coder) implements from the spec — permission flow (T-002.1),
  receiver/registrar bodies + confidence window + Hilt + manifest, `MT-ActivityRecognition` logging,
  wire automatic-start into the service without duplicating manual-start; then `./gradlew test`.
  T-002.5 (Bluetooth, off-by-default) deferred to end of T-002.~~
- ✅ done 2026-06-22 (android-engineer, T-002.4 chunk): `ConfidenceAcquisitionWindow`/`Impl` +
  `ConfidenceUpdateReceiver` + `ActivityUpdatesRegistrar` seam built per spec §4; Hilt rebound
  `VehicleEntryConfidenceGateway` from the `NoOp` placeholder to the real impl (bound `@Singleton`,
  a documented deviation from the spec's suggested `@ServiceScoped` — both consuming
  `BroadcastReceiver`s can run independently of the service's lifecycle). `./gradlew test`: 26
  tests, 0 failures, including the 4 new spec §5 virtual-time test cases.
- ~~**Next (still open):** T-002.1 (permission-request screen, not built yet) and wiring
  `ConfidenceAcquisitionWindow.observeResults()` into `TripTrackingForegroundService` (the shared
  insert/notify tail extraction) — deliberately deferred chunks, not yet done. T-002.5 (Bluetooth,
  off-by-default) extended scope below.~~
- ✅ done 2026-06-22 (android-engineer, foreground-service wiring chunk): `ConfidenceAcquisitionWindow`
  injected into `TripTrackingForegroundService`; its `observeResults()` Flow is collected exactly
  once in a new `onCreate()` (never re-subscribed per `onStartCommand`). Each emitted `TripStartEvent`
  goes through a new `handleAutomaticStartEvent()` — acquires `tripLifecycleMutex`, re-runs the
  no-op guard, calls `tripLifecycleStateMachine.onStartEvent(...)`, then a new shared-tail method
  `completeTripStart(startedAtEpochMillis)` extracted from the old `handleStartTripRequested()` body
  (insert trip, start location updates, reset both stop timers) — no duplicated insert/notify logic
  between manual and automatic start. Per-event `CancellationException`-rethrow / catch-and-log
  boundary lives inside the handler (not wrapped around `collect`) so one bad confidence-window
  result can't silently kill the subscription for the rest of the service's life. `onDestroy()` got
  a new `runCatching { confidenceAcquisitionWindow.cancel() }` step alongside the existing teardown
  steps. Class doc comment updated (automatic start is now wired, not "registered but not yet
  wired"). `./gradlew test`: 26/26 green, no regressions. `./gradlew assembleDebug`: clean build.
  No new unit test for the service itself (still untestable without Robolectric under this
  project's no-mocking convention — consistent with no existing `TripTrackingForegroundServiceTest`).
  **Still open:** T-002.1 (permission-request screen) and T-002.5 (Bluetooth, off-by-default,
  extended-scope idea below).
- 🐛 **Field finding 2026-06-23 (user's first sideload test, not yet fixed — "make a note, don't fix
  now"):** `SetupPermissionsScreen`'s system permission dialog did not fire automatically; user had
  to grant all runtime permissions manually via phone Settings instead. Root cause not yet
  investigated. Full detail in `team/LOGS.md`'s 2026-06-23 NOTE entry.
- ✅ done 2026-06-23 (android-engineer, T-002.1 — finishes the incomplete shell, closes the field
  finding investigation): full detail in `team/LOGS.md`'s 2026-06-23 11:15 DONE entry. Summary:
  - New `SetupPermissionsPlanner` (pure Kotlin) decides first-round permissions-still-needed,
    whether to fire the **separate, second-step** `ACCESS_BACKGROUND_LOCATION` request (required on
    API 29+ — bundling it into the first multi-permission call silently fails to grant it), and the
    brief §8-exact limited-mode rule (background location OR notifications denied — camera alone
    does not count).
  - `SetupPermissionsUiState`'s granted flags are now real: updated from both launcher callbacks'
    actual results AND a `ContextCompat.checkSelfPermission` read on initial composition (so
    returning users see accurate state).
  - **Restricted-settings theory: investigated and RULED OUT, not assumed true or silently
    skipped.** Android 13+ restricted settings blocks Accessibility/Notification-Listener/Device-
    Admin grant screens specifically — not the standard dangerous-permission dialog this screen
    uses. The actual explanation: the dialog only ever fires from the Continue button's `onClick`
    (never automatically), so a tester who didn't tap it, or hit an OS "don't ask again" suppression
    after a prior denial, would see exactly this symptom with no OS/installer-level block involved.
    Added a permanently-visible in-app advisory line on the screen as a safety net regardless of
    root cause.
  - **NavHost first-run bug (found fresh this session):** `startDestination` was hard-coded to
    `SetupPermissions` on every launch, ignoring the already-persisted `hasCompletedFirstRunSetup`
    flag. Fixed via new `StartDestinationViewModel` + a brief loading-spinner gate (NavHost's
    `startDestination` can only be set once, at first composition).
  - `MT-UI` logging added (Continue tapped, per-permission grant/deny, background-location
    second-step outcome) per the now-standing logging convention.
  - **Tests:** `SetupPermissionsPlannerTest` (14 cases), `SetupPermissionsViewModelTest` (5 cases),
    `StartDestinationViewModelTest` (2 cases) — all hand-written fakes, no mocking framework. New
    `FakeSettingsRepository` added.
  - `./gradlew test`: debug 59/59 passed, release 59/59 passed (11 test classes each, 0
    failures/errors — JUnit XML read directly). `./gradlew assembleDebug`: BUILD SUCCESSFUL.
  - Not committed/pushed — awaiting explicit user go-ahead per the standing rule. T-002.5
    (Bluetooth extended scope) remains parked/deferred, untouched.
  - **T-002 is now fully closed** (detection pipeline + Setup/Permissions screen both done) modulo
    the deliberately deferred T-002.5.
- ✅ done 2026-06-22 (android-engineer, app-launch auto-start chunk — closes the gap flagged directly
  below): `MainActivity.onCreate()` now starts `TripTrackingForegroundService` with **no action
  set** (plain `Intent`, not `ACTION_START_TRIP`) via `ContextCompat.startForegroundService(...)`,
  before `setContent { ... }`, with an `MT-Service` Timber log line at the call site. Chosen over
  `MileageTrackerApplication.onCreate()` (riskier pre-Activity foreground-service start on some
  OEM builds). Confirmed (not assumed) that `ActivityRecognitionRegistrar.register()` is
  idempotent-safe to call again on every launch/`onStartCommand` per its own class doc (Play
  Services treats same-PendingIntent re-registration as a safe no-op) — no change needed there.
  Deliberately not gated on any permission: a no-action `onStartCommand` only re-runs the already
  no-op-safe recovery check and re-registers transition updates; the service's GPS path already
  gates on `hasFineLocationPermission()` independently, and Play Services' own failure listener
  degrades gracefully without that permission. `TripTrackingForegroundService`'s class doc comment
  extended to document this third entry point. Scoped deliberately per the user's own framing
  ("today is all about detection — we can later make it always on but sleeping, wakes on
  detection") — explicitly NOT built: boot-completed receiver, Settings toggle, wake-from-dead-
  process redesign. `./gradlew test`: BUILD SUCCESSFUL, 26 tests × debug+release = 52, 0 failures.
  `./gradlew assembleDebug`: BUILD SUCCESSFUL. Full detail in
  `team/blueprints/T-002-vehicle-detection-spec.md` §6a. Not committed — awaiting explicit
  user go-ahead per standing rule.
- ⚠️ **NEW gap found 2026-06-22 (Manager, while answering "will it actually detect" — not previously
  flagged in the spec or board):** `TripTrackingForegroundService` — and therefore
  `ActivityRecognitionRegistrar.register()`, and therefore the entire T-002 automatic-detection
  pipeline just wired above — only ever starts via `HomeStatusViewModel.onStartTripClicked()` (the
  manual "Start Trip" button on the Home screen), which itself immediately begins a manual trip.
  There is no boot receiver, no `Application.onCreate` auto-start, and no "watch passively for
  vehicle entry" entry point — confirmed by grepping every reference to
  `TripTrackingForegroundService` in `app/src/main`. `RECEIVE_BOOT_COMPLETED` is declared in the
  manifest but nothing implements a receiver for it. The service's own
  `buildPersistentNotification()` already has a "Watching for trip activity" idle-state string,
  implying the original design intent was a continuously-running service — but no code path reaches
  that idle state today. **Practical effect: as currently wired, the app cannot auto-detect a drive
  on a real phone, because the detection code only runs once a trip has already been started
  manually** (at which point automatic detection is moot — a trip is already active). This blocks
  "sideload it and see automatic detection work" until closed. Also separately confirmed
  `SetupPermissionsScreen` (built earlier, likely under T-017) requests
  `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`/`CAMERA`/`ACTIVITY_RECOGNITION`/`POST_NOTIFICATIONS`
  but never requests `ACCESS_BACKGROUND_LOCATION` (a required second-step request on Android 10+),
  and `SetupPermissionsViewModel`'s `SetupPermissionsUiState` granted-flags are never actually
  updated from the permission-launcher result (`isFineLocationGranted` etc. stay hard-coded `false`)
  — so T-002.1 is not a from-scratch build, it's finishing an existing incomplete shell. Not yet
  actioned; surfaced for a decision on priority, see `team/SESSION_HANDOFF.md`/conversation.
- 🗒️ **Extended scope idea logged 2026-06-22 (user request, deferred — not MVP):** T-002.5 to grow
  beyond a simple on/off trigger into named per-vehicle profiles (`my_vehicle_1/2/3`), saved
  pairing config, used as an *additional* confirmation signal alongside motion-based detection
  (not a replacement, since auto-connect reliability varies a lot by vehicle/head-unit — the
  user's own car requires a manual head-unit selection). Also floated: log Bluetooth
  connect/disconnect state alongside the other detection signals as ground truth, to eventually
  feed a future ML model that improves detection accuracy over time. Explicitly not needed for
  the MVP — parked here for whenever T-002.5 is actually picked up.

### T-003 · Trip classification flow (Work/Private + mandatory business reason)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** T-001
- HIGH-importance notification channel `mileage_tracker_trip_alerts`, lock-screen action, pending states.
- ✅ done 2026-06-23 (android-engineer): notification-fire + lock-screen-open + pending-nav UX built
  and verified. Confirmed scope: single notification, fires when a trip enters PENDING_OCR (trip
  STOP), for every trip regardless of start method — this supersedes the brief's literal "notify at
  START" wording; the existing classify-at-stop architecture (HomeStatusScreen's in-app
  LaunchedEffect auto-route) is unchanged.
  - **Notification fire:** `TripTrackingForegroundService.notifyTripAwaitingClassification(tripId)`
    called from the single shared `handleStopEvent` tail (all 3 stop paths — manual, 3-min
    ConfirmedInactivity, 2-min UnstableSignalTimeout — land here), right after
    `tripRepository.updateStatus(tripId, resolvedStatus)`. Not duplicated per-path. Notification id
    = `tripId.hashCode()` (matches the existing `TripClassificationNotificationBuilder` PendingIntent
    request-code convention) so re-stop/recovery never stacks duplicates — `notify()` with the same
    id replaces in place.
  - **Nav seam (new):** `PendingTripClassificationNavigationStore` (`@Singleton`, plain
    `MutableStateFlow<String?>` + `setPendingTripId`/`consumePendingTripId`) in
    `ui/navigation/PendingTripClassificationNavigationStore.kt`. `MainActivity.onCreate` +
    new `onNewIntent(Intent)` override both extract `ACTION_OPEN_TRIP_CLASSIFICATION` /
    `EXTRA_TRIP_ID` and call `setPendingTripId`. `MileageTrackerNavHost` takes the store as an
    optional param, observes `pendingTripId` via `collectAsState()` inside a
    `LaunchedEffect(pendingTripId)`, calls `consumePendingTripId()` (clearing it) before
    `navController.navigate(Screen.TripClassification.buildRoute(tripId))` — consumed exactly once,
    survives rotation safely because the store is Hilt-`@Singleton`-scoped (outlives the recreated
    Activity) while the *consumed* state still prevents replay.
  - **Lock-screen wake-and-open:** `MainActivity.onCreate` calls `setShowWhenLocked(true)` /
    `setTurnScreenOn(true)` (API 27+, always available at minSdk 29) as defense-in-depth alongside
    the manifest's existing `android:showWhenLocked`/`turnScreenOn` attributes. Deliberately did
    **not** add `setFullScreenIntent` on the notification — brief §5.2 only requires wake-on-tap, not
    an unprompted heads-up-over-lockscreen popup, and `USE_FULL_SCREEN_INTENT` carries elevated-
    permission/OEM-revocation risk on API 33+ that a tap-driven design avoids entirely. Fallback
    (brief §5.2: "must still open the app normally" if lock-screen interaction is disallowed) needs
    no extra code — that's Android's own default behavior when `showWhenLocked` is refused by device
    policy (requires unlock, then shows the Activity normally).
  - **Test:** `PendingTripClassificationNavigationStoreTest` (7 cases, plain JUnit, no Android
    framework dependency) — initial-null, set/observe, consume-clears, consume-when-empty,
    double-consume-only-fires-once, set-after-consume, overwrite-not-yet-consumed.
  - **MT-UI/MT-Trip logging:** added. `MainActivity.handleTripClassificationIntent` logs `MT-UI` on
    notification tap (tripId) and `MT-UI` (`.e`) on a malformed intent (missing/blank tripId).
    `TripTrackingForegroundService.notifyTripAwaitingClassification` logs `MT-Trip` (trip-stop ->
    notification-posted is itself a trip-state event, consistent with T-022's "DB read/write or
    state consequence of a user/system action" bar) and `MT-Service` (`.e`) if `NotificationManager`
    is unavailable. T-022 didn't cover this because the notification never fired when T-022 was
    built — this is the first real coverage of it.
  - **Verification:** `./gradlew test` — JUnit XML read directly (not just "BUILD SUCCESSFUL"):
    debug 38/38 passed (0 failures/errors across 8 test classes), release 38/38 passed (same).
    `./gradlew assembleDebug` also green — confirms the Hilt graph resolves with the new
    `@Inject` fields (`PendingTripClassificationNavigationStore` in `MainActivity`,
    `TripClassificationNotificationBuilder` in `TripTrackingForegroundService`).
  - **Not committed/pushed** — awaiting explicit user go-ahead per project rule.

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
- ✅ done 2026-06-22 (android-engineer, small UI chunk — user request: "add a big status indicator
  for the detection — red or green, green if it is detected"): `HomeStatusScreen.kt` gained a large
  rounded `Box` at the top of its Column, filled `Color(0xFF2E7D32)` (green) when
  `uiState.inProgressTrip != null` else `Color(0xFFC62828)` (red), 120dp tall, with a bold white
  "Detected"/"Not detected" text label inside it (not color-only, for colorblind accessibility).
  Deliberately keyed off `inProgressTrip != null` rather than `isTrackingActive` alone — that's the
  more honest "did detection actually produce a trip" signal (it also covers the brief PENDING_OCR
  "finishing up" tail, which `isTrackingActive` excludes). Pure derived-from-existing-state UI; no
  ViewModel changes, no detection-logic changes. No existing `MileageTrackerTheme`/status-color
  token convention was found in the codebase to reuse, so plain Material3-friendly literal colors
  were used rather than inventing a new design-token system for one indicator.
  `./gradlew test` → BUILD SUCCESSFUL (61 actionable tasks, all green). `./gradlew assembleDebug` →
  BUILD SUCCESSFUL. Not committed — awaiting explicit user go-ahead per standing rule.
- 🐛 **Field finding 2026-06-23 (user's first sideload test, not yet fixed — "make a note, don't fix
  now"):** the "Detected"/"Not detected" label is misleading for a manually-started trip — tapping
  "Start trip" yourself also shows "Detected," even though nothing was actually detected by the
  ActivityRecognition pipeline. User's framing: manual start is "the user override" and should read
  something like "Manual trip," not "Detected." Likely needs the trip's origin (manual vs.
  automatic) to be tracked somewhere it isn't today. Full detail in `team/LOGS.md`'s 2026-06-23 NOTE.
- ✅ done 2026-06-23 (small label fix, user request — not deferred, applied immediately): Settings
  screen's debug-log export button text changed from "Export debug log" to "Export debugging logs"
  in `ui/settings/SettingsScreen.kt`, per the user's framing that this logging is pure debugging
  output (like a Python print statement), not a user-facing feature. Not yet rebuilt onto the
  sideloaded APK on the user's phone — takes effect next rebuild/reinstall.

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

### T-020 · Debug-only Bluetooth connection diagnostic logging — ✅ DONE 2026-06-22
- **Owner:** android-engineer · **Phase:** Cross-cutting (dev/testing tool, not a user feature) · **Blocked-by:** none
- ✅ done 2026-06-22: `BluetoothDiagnosticsSnapshot` interface (shared, `src/main`) + real impl in
  `app/src/debug/...` (dynamically-registered `BroadcastReceiver` for ACL connect/disconnect, held
  inside the debug-only `@Singleton` itself — not a manifest receiver, so the "doesn't exist in
  release" property lives in one file pair) + no-op impl in `app/src/release/...`. `BLUETOOTH_CONNECT`
  (API 31+) / legacy `BLUETOOTH` (maxSdk 30) declared ONLY in `app/src/debug/AndroidManifest.xml`.
  Wired into all 3 `MT-ActivityRecognition` log lines in `ConfidenceAcquisitionWindowImpl` (per-reading,
  confident-entry, retry-exhausted) via Hilt (`BluetoothDiagnosticsModule`, `@Binds @Singleton`).
  Graceful degradation: falls back to the device's MAC address as the label if `BLUETOOTH_CONNECT`
  isn't granted (no in-app prompt — user grants manually on their own test device), one-time
  `MT-Bluetooth` warning log instead of repeated spam, `SecurityException` guarded.
  `./gradlew clean test assembleDebug` — BUILD SUCCESSFUL, all 6 test classes × 2 variants (30 tests
  each), 0 failures. **Verified release has zero footprint:** inspected the merged release manifest
  (`processReleaseManifestForPackage`) — no `BLUETOOTH_CONNECT`/`BLUETOOTH` anywhere; present in the
  debug merged manifest as expected.
- 🐛 **Real pre-existing issue found and fixed (not part of this task's original ask):**
  `app/src/main/AndroidManifest.xml` already speculatively declared `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`
  from T-001 scaffolding — left in place, this would have shipped the Bluetooth permission to every
  field tester regardless of today's debug-only work, defeating the entire point. Removed from
  `src/main`; now exists only in the debug-only manifest as intended.
- ⏳ Scoped to `ConfidenceAcquisitionWindowImpl`'s 3 log lines only (per the brief's own allowance) —
  `ActivityTransitionReceiver`'s ENTER/EXIT lines do NOT carry the Bluetooth snapshot yet (would need
  injecting the interface into a second `BroadcastReceiver` for a purely-informational line with no
  confidence value attached; judged not worth the added surface). Easy follow-up if ever wanted.
- Not committed yet — awaiting explicit go-ahead per standing rule.
- Added 2026-06-22 — user request, scoped down from the bigger T-002.5 extended-scope idea above to
  the minimum useful slice for the user's *own* testing only (explicitly NOT for field testers):
  a single log line field — `bluetoothConnected: true/false` + the connected device's name/id —
  recorded alongside the existing `MT-ActivityRecognition` log lines (confidence readings,
  confident-entry/retry-exhausted outcomes). Purpose, in the user's own words: read the logs after
  a real drive, see e.g. "detection probability 80%, bluetooth was true," and be able to ask
  "were you in your car here?" to manually confirm/deny — tightening the detection thresholds
  toward 99–100% accuracy over time. Not a detection trigger, not a UI feature, no vehicle
  profiles — purely an observability signal for this one task.
- **Decided 2026-06-22 (user confirmed via question, then clarified further):** must be debug-build
  only — implemented via Gradle build-type source sets (`app/src/debug/...` real impl incl. the
  `BLUETOOTH_CONNECT` manifest permission declared only in `src/debug/AndroidManifest.xml`;
  `app/src/release/...` no-op impl, no permission) so field testers' release build never requests
  the permission or logs anything Bluetooth-related — it does not exist in their build at all.
- No UI permission-grant flow planned (debug-only diagnostic tool, not worth building a screen
  for) — if the runtime permission isn't already granted on the test device, log a graceful
  "Bluetooth diagnostics unavailable, permission not granted" line rather than crashing; the user
  can grant it manually for their own test device.

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

### T-021 · All-in-one dev environment startup script — ✅ DONE 2026-06-22
- **Owner:** devops-engineer · **Phase:** Cross-cutting (dev tooling, not a product feature) · **Blocked-by:** none
- Added 2026-06-22 — user request: reloading both halves of the dev environment (Python backend +
  Android emulator + app) by hand every session was getting tedious; wanted one idempotent script
  that checks what's already running and only starts what's missing.
- ✅ done 2026-06-22: `scripts/start-dev.ps1`. Backend: creates `backend/.venv` + installs
  `requirements.txt` only if missing, checks `http://127.0.0.1:8080/health` before starting
  `python app.py` (via the venv's own `python.exe` directly — no shell activation needed) so it
  never double-starts. Android: fails loudly if `ANDROID_HOME`/`ANDROID_SDK_ROOT` isn't set or
  doesn't exist (no silent fallback); detects an already-running emulator via `adb devices`,
  otherwise boots `test_device` in the background and polls `sys.boot_completed` up to a 90s
  timeout. Always runs `gradlew.bat installDebug` (the actual "reload") and launches via
  `adb shell am start -n com.mileagetracker.app/.MainActivity`. Prints a clear skipped-vs-started
  summary; surfaces real Gradle failures instead of swallowing them. Bonus `-Stop` switch stops only
  the processes the script itself started (tracked in git-ignored `scripts/.dev-pids.json`) — does
  not hunt for or kill unrelated processes.
- Verified end-to-end (cold start + immediate re-run + `-Stop`): backend reachable, emulator
  detected/booted, `installDebug` BUILD SUCCESSFUL, app launched and confirmed foregrounded via
  `adb shell dumpsys activity activities` + `adb shell ps`. Re-run correctly skipped venv/Flask/
  emulator (already up) while still re-running `installDebug` every time.
- ⚠️ Two deviations from the literal brief, both evidence-based (found by actually testing, not
  guessed): (1) health-check uses `127.0.0.1` instead of `localhost` — PowerShell 7's `HttpClient`
  intermittently stalls on `localhost` trying the IPv6 route first before falling back, even though
  Flask binds `0.0.0.0`; (2) liveness-probe catch blocks are broadened (not typed to
  `WebException`) because PowerShell 7 throws `TaskCanceledException` on timeout instead — the real
  exception text is still surfaced via `-Verbose`, this is the legitimate "check first" pattern, not
  error suppression.
- Not committed yet — awaiting explicit go-ahead per standing rule (along with T-002.4/T-020 above).
- 🔁 **Revised 2026-06-22 (user, after first run): kill-and-restart-fresh, not skip-if-running.** The
  user ran the script, saw Flask/emulator skipped because they were already up from earlier in the
  session, and decided that wasn't what they wanted — every run should kill whatever's currently
  running and start it fresh (confirmed explicitly, including for the emulator's ~10-30s reboot
  cost, after being shown the trade-off). Updated: backend now always finds+kills whatever's
  listening on port 8080 (via `Get-NetTCPConnection`, not just the script's own PID-file memory) and
  starts Flask fresh; emulator now always kills any running `emulator-XXXX`, confirms it's actually
  gone from `adb devices` before proceeding, then always reboots `test_device` fresh. `.venv`
  directory creation is still create-if-missing (no pip reinstall every run — that's not what was
  asked). Verified with 2 consecutive runs showing different PIDs both times (no skip branch left);
  measured emulator kill+reboot time ~11-28s.

### T-022 · Full interaction + persistence audit logging (screen/button/field/DB read-write trail)
- **Owner:** android-engineer (design) + android-coder (mechanical grind, per the T-018 pattern) · **Phase:** MVP/Cross-cutting · **Blocked-by:** T-018 (builds on its `MT-*` Timber/file-log convention)
- 🔄 **Status update 2026-06-23 (android-engineer):** back-loop bug **fixed** (see below, was previously
  the un-actioned 🐛 note). Tag scheme designed + reference-implemented in full in `HomeStatusViewModel`
  (`MT-UI` for user-initiated actions, `MT-Trip` for the DB read/write tied to that action — full spec
  in `team/blueprints/T-022-audit-logging-spec.md`, written for `android-coder` to replicate mechanically
  into `TripClassificationViewModel`/`OdometerCaptureViewModel` with no design decisions left open).
  `./gradlew clean test` → BUILD SUCCESSFUL, 31/31 pass (debug + release). **Still open:** the mechanical
  rollout into `TripClassificationViewModel`, `OdometerCaptureViewModel`, and the `SettingsViewModel`/
  `ExportViewModel` completeness audit — hand `team/blueprints/T-022-audit-logging-spec.md` to
  `android-coder` next.
- Added 2026-06-23 — user's first real sideload test surfaced a genuine blind spot: T-018's logging
  only covers the service/data layer (foreground service, ActivityRecognition, location, OCR, CSV,
  Room write paths) — none of the screen-level ViewModels log anything. During testing, the user hit
  a real bug (see below) and the debug log was completely silent about the actual sequence of
  screens/buttons/fields involved, even though it correctly showed the service stayed healthy
  underneath. User's own framing for the scope: log **which screen** is shown, **what button** was
  pressed, **what was entered** in each field, **what gets saved** to the DB, and **what gets read**
  from the DB — a full interaction + persistence trail, not just the lower-level plumbing.
- Concretely, at minimum: `HomeStatusViewModel` (Start/Stop trip clicks), `TripClassificationViewModel`
  (Work/Private selection, business-reason text, Save click + validation outcome),
  `OdometerCaptureViewModel` (entered/OCR'd value, save), `SettingsViewModel`/`ExportViewModel` (already
  partially covered by T-018/T-019, audit for completeness) — each screen's user-initiated actions and
  the exact DB row read/written as a result, using the existing `MT-*` Timber tag convention (likely
  new tags, e.g. `MT-UI`/`MT-Trip` — exact tag scheme is a design call for android-engineer, not
  decided here).
- 🐛 ~~**The bug that surfaced this gap, not yet fixed (user explicitly deferred to next session, bundled
  with this task):**~~ <!-- SUPERSEDED 2026-06-23: fixed this session, see ✅ note immediately below —
  original description retained for the audit trail, not deleted, per no-delete rule. -->
  ~~after Stop Trip, `TripClassificationScreen` is reached via
  `HomeStatusScreen`'s `LaunchedEffect` (fires whenever `inProgressTrip?.status == TripStatus.PENDING_OCR`).
  Pressing the system Back button DOES pop back to Home as normal — but Home's same `LaunchedEffect`
  immediately re-fires (the trip is still PENDING_OCR) and instantly re-navigates to
  `TripClassification`, every single time. The user experienced this as "Back is completely blocked,
  it's modal" and had to repeatedly force-close/reopen the app to escape it (visible in the debug log
  as 8 repeated "Auto-starting detection service from app launch" lines in under 2 minutes — most
  likely from those repeated relaunches, though T-022's new logging would be needed to actually
  *confirm* that rather than infer it from the code, which is exactly the gap this task closes).
  Root cause confirmed by reading `TripClassificationScreen.kt` (no `BackHandler`, nothing intentionally
  blocks Back) and `HomeStatusScreen.kt`'s `LaunchedEffect` (re-triggers unconditionally on every
  recomposition where the trip is still PENDING_OCR, with no "user explicitly chose to leave" escape
  hatch). **Not fixed this session — user asked to write it up for a fresh session instead of
  continuing on a near-exhausted context window.**~~
- ✅ **Back-loop bug fixed 2026-06-23 (android-engineer).** `HomeStatusUiState.autoRoutedToClassificationTripId`
  gates `HomeStatusScreen`'s `LaunchedEffect` to auto-navigate at most once per trip id (set via the
  new `HomeStatusViewModel.onTripClassificationAutoRouted`). Since the trip is still genuinely
  un-classified after Back, Home now also shows an explicit "Resume classification" button
  (`HomeStatusUiState.showResumeClassificationAction` / `onResumeClassificationClicked`) so the trip is
  never stranded with no way back in. Covered by new `HomeStatusViewModelTest` (5 cases) + a new
  hand-written `FakeTripRepository`. Files: `app/src/main/kotlin/com/mileagetracker/app/ui/home/HomeStatusViewModel.kt`,
  `.../ui/home/HomeStatusScreen.kt`, `app/src/test/kotlin/com/mileagetracker/app/ui/home/HomeStatusViewModelTest.kt`,
  `app/src/test/kotlin/com/mileagetracker/app/domain/repository/FakeTripRepository.kt`. Full reasoning
  in `team/LOGS.md`'s 2026-06-23 DONE entry.
- **Definition of done:** every screen's user-initiated action and every DB read/write tied to it is
  traceable from the exported debug log alone, no source-reading required to reconstruct "what did the
  user actually do and what did the app actually persist." ~~The back-loop bug above should be fixed as
  part of (or immediately after) this task, ideally verified via the new logging rather than just
  code-reading.~~ <!-- SUPERSEDED 2026-06-23: back-loop bug is fixed (see ✅ note above); the mechanical
  MT-UI/MT-Trip rollout into TripClassificationViewModel/OdometerCaptureViewModel/Settings/Export is
  still the open remainder of this task's definition of done. -->
- ✅ **T-022 closed out 2026-06-23 (android-engineer, final review + end-to-end verification pass).**
  `android-coder`'s mechanical rollout of §5.1/§5.2/§5.3 into `TripClassificationViewModel.kt`,
  `OdometerCaptureViewModel.kt`, `SettingsViewModel.kt`, `ExportViewModel.kt` reviewed line-by-line
  against `team/blueprints/T-022-audit-logging-spec.md` — every log line matches the spec exactly
  (placement, message text, `%s`-style tripId interpolation), no non-logging behavior changed, no
  `Timber` calls leaked into any `*Screen.kt` Composable, no third tag invented beyond `MT-UI`/`MT-Trip`.
  **No fixes needed — the coder's diff was correct as submitted.**
  **Verification method: live emulator walkthrough** (not code-trace) — `test_device` (API 34) was
  already running, so the stronger check was used: `./gradlew assembleDebug` → installed via `adb install
  -r` → drove the full flow by `adb shell input tap`/`uiautomator dump` (Start Trip → Stop Trip →
  auto-navigate to Classification → select Work → type business reason "ClientSiteVisit" → Save →
  Odometer Capture manual entry "45231.5" → Confirm → back on Home with the trip COMPLETED), then pulled
  the real on-device debug log via `adb shell run-as com.mileagetracker.app cat
  .../files/logs/mileage_tracker_log.txt` (NOT logcat — `FileLoggingTree` is a file-only sink by design,
  T-018; `MT-*` tags never reach `adb logcat`, only the on-device log file). The pulled log reconstructed
  the entire flow with no source-reading: `MT-UI`/`MT-Trip` lines for Start, Stop, auto-navigate, trip
  load, classification select, Save (+ the write line carrying `businessReason=ClientSiteVisit`),
  manual-odometer confirm (+ write line carrying `valueKm=45231.5`), and the `PENDING_OCR -> COMPLETED`
  resolution line — tripId threaded through every line, and the Home-dispatch lines correctly say the
  actual DB write happens inside the service (`MT-Service`/`MT-Repository` pick up the trail there), not
  in the ViewModel. Also independently exercised `ExportScreen` (Export button click logged, CSV write
  succeeded — "Exported 3 trips") and `SettingsScreen` (both the photo-retention toggle and the
  Bluetooth-trigger toggle logged, plus the "Export debugging logs" button click logged and the export
  itself succeeded) — all covered, no gaps, no duplicated/relocated `MT-Export` lines.
  **Back-loop fix re-verified live, not just by re-reading code:** started a second trip, stopped it
  (auto-navigated to Classification), pressed system Back → landed on Home and **stayed there**
  (confirmed via a second UI dump after a pause — no auto-bounce-back), showing "Last trip still needs
  classification" + a "Resume classification" button; tapped it → correctly returned to
  `TripClassificationScreen` for the same trip. The pulled log shows the gate holding: exactly one
  "auto-navigated to TripClassification" line for that tripId, then later an explicit "Resume
  classification button clicked" line for the same tripId with no second auto-navigation line in
  between — proof the `autoRoutedToClassificationTripId` gate did its job, read from the log alone.
  `./gradlew assembleDebug` succeeded cleanly (BUILD SUCCESSFUL, 41 tasks). No commit/push performed —
  awaiting explicit user go-ahead per standing rule.

## Done

### T-001 · Scaffold Android project (Kotlin, Compose, Room, Hilt) — ✅ done 2026-06-22
- First MVP task fully complete: scaffold built, both review blockers resolved (already fixed in
  `c846b05`), re-verified green (`./gradlew clean test`, all 5 test classes, debug + release), committed
  and pushed (`origin/main`). Full card with the per-surface detail is retained in the **In Progress**
  section above (annotated DONE in place per the no-delete rule). Unblocks T-002–T-007 and T-017.

<!-- REMOVED (reason: T-001 is now the first completed task — list is no longer empty)
_(none yet — completed tasks move here with a `✅ done YYYY-MM-DD` tag)_
-->
