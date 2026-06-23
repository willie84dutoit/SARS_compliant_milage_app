# Kanban Board

Last moved: 2026-06-23 · Maintained by the Manager. WIP limit on **In Progress** = 3.
Story details in `../userstories/`; task breakdown in `../team/TASKS.md`; the literal build steps
each checkbox below refers to live in `../team/blueprints/FULL_IMPLEMENTATION_PLAN.md` (`T-XXX.Y`
ids match exactly — open that file for the How/Verify behind any box here).

> Every card names its **owner role** (the specialist responsible) — never delete that line when
> editing a card, even if the checklist underneath changes.

---

## 📋 Backlog

### T-004 · GPS distance tracking + trip-end detection — US-004
**Owner role:** _geo-sensors-specialist_ · Blocked-by: T-002
- [ ] T-004.1 Configure the location request
- [ ] T-004.2 Specify the distance-accumulation function
- [ ] T-004.3 Specify the GPS noise floor
- [ ] T-004.4 Specify the two stop timers
- [ ] T-004.5 Specify Room write batching for distance

### T-005 · Odometer OCR capture + manual fallback — US-003
**Owner role:** _ml-ocr-specialist + android-engineer_ · Blocked-by: T-001
- [ ] T-005.1 Specify the capture screen
- [ ] T-005.2 Specify the digit-extraction regex
- [ ] T-005.3 Specify the OCR client and its confidence score
- [ ] T-005.4 Specify the sanity check before trusting any OCR result
- [ ] T-005.5 Specify the manual fallback
- [ ] T-005.6 Specify photo retention

### T-006 · Local persistence + recovery (Room) — US-005
**Owner role:** _android-engineer_ · Blocked-by: T-001
- [ ] T-006.1 Specify the Trip table (incl. T-008's signing columns from day one)
- [ ] T-006.2 Specify the DAO queries
- [ ] T-006.3 Specify the database class
- [ ] T-006.4 Specify the no-duplicate-trip recovery mechanism
- [ ] T-008.1–T-008.5 (signing implementation — see T-008's Done card below; rides with this task)

### T-007 · CSV export to Downloads — US-006
**Owner role:** _android-engineer_ · Blocked-by: T-006
- [ ] T-007.1 Specify the export filter
- [ ] T-007.2 Specify the fixed column order
- [ ] T-007.3 Specify the file write

### T-009 · Backend sync API (Flask/Cloud Run/Firestore) — US-102, US-105, US-107
**Owner role:** _backend-engineer (+ security-crypto-specialist)_ · Blocked-by: T-006, T-008
- [ ] T-009.1 Specify the project setup
- [ ] T-009.2 Specify server-side auth verification
- [ ] T-009.3 Specify the idempotent sync endpoint
- [ ] T-009.4 Specify the security rules
- [ ] T-009.5 Specify photo upload
- [ ] T-009.6 Specify account deletion

### T-011 · Analytics events + SARS-ready reporting — US-103, US-104
**Owner role:** _analytics-specialist (+ backend-engineer)_ · Blocked-by: T-009
- [ ] T-011.1 Specify the event taxonomy
- [ ] T-011.2 Specify client instrumentation
- [ ] T-011.3 Specify the admin review query
- [ ] T-011.4 Specify the SARS export query

### T-012 · Store publishing readiness (Play / App Store / Huawei) — US-202, US-204
**Owner role:** _compliance-qa-specialist_ · Blocked-by: T-004, T-005
- [ ] T-012.1 Specify the disclosure screen
- [ ] T-012.2 Specify the review video
- [ ] T-012.3 Specify the iOS usage-description strings
- [ ] T-012.4 Specify the privacy policy
- [ ] T-012.5 Specify the data-safety forms
- [ ] T-012.6 Specify the Play closed-testing requirement
- [ ] T-012.7 Specify the Huawei submission

### T-013 · iOS port (Swift, CoreMotion, CoreLocation, Vision) — US-201
**Owner role:** _ios-engineer_ · Blocked-by: stable MVP + backend contract
- [ ] T-013.1 Specify detection
- [ ] T-013.2 Specify the notification
- [ ] T-013.3 Specify location tracking
- [ ] T-013.4 Specify OCR
- [ ] T-013.5 Specify authentication
- [ ] T-013.6 Specify the data contract

### T-016 · Huawei HMS technical adaptation (no GMS) — US-203
**Owner role:** _geo-sensors-specialist + ml-ocr-specialist (+ backend-engineer)_ · Blocked-by: T-002, T-005, T-009
- [ ] T-016.1 Specify the build split
- [ ] T-016.2 Specify the auth swap
- [ ] T-016.3 Specify the detection swap
- [ ] T-016.4 Specify the OCR swap
- [ ] T-016.5 Specify the sync-routing swap

### T-017 · Compose UI screens — layout, navigation, visual design
**Owner role:** _android-engineer (+ a11y-architect)_ · Blocked-by: T-001
- [ ] T-017.1 Specify the navigation graph and shared theme
- [ ] T-017.2 Build SetupPermissionsScreen
- [ ] T-017.3 Build HomeStatusScreen
- [ ] T-017.4 Build TripClassificationScreen
- [ ] T-017.5 Build OdometerCaptureScreen
- [ ] T-017.6 Build TripHistoryScreen and ExportScreen
- [ ] T-017.7 Build SettingsScreen
- [ ] T-017.8 Accessibility pass across all 7 screens

### T-018 · Field-debuggability — structured logging + error-handling boundaries — DONE 2026-06-22
**Owner role:** _android-engineer (design) + android-coder (grind)_ · Blocked-by: T-001 · added 2026-06-22
- [x] T-018.1 Timber 5.0.1 + `FileLoggingTree` (rotating log file under `filesDir`, planted in Application, $0/on-device)
- [x] T-018.2 "Export debug log" action on Settings (`DebugLogFileProvider`, reuses MediaStore share pattern)
- [x] T-018.3 try/catch + `MT-*` tagged logging at 6 of 7 surfaces (service, location, OCR, CSV, Room) — _ActivityRecognition logging deferred to T-002 (its real registration call doesn't exist yet)_
- [x] T-018.4 OCR exceptions now degrade to `NoTextFound`, can't trap a trip in `pending_ocr`
- _Verified via android-engineer review pass; `./gradlew test` green._

### T-019 · Move file/MediaStore I/O off the main thread — DONE 2026-06-22
**Owner role:** _android-coder (grind, per android-engineer review finding)_ · Blocked-by: T-018
- [x] Wrap `CsvFileWriter.writeToDownloads(...)` call in `withContext(Dispatchers.IO)` (ExportViewModel) — fixes a UI-freeze/ANR risk on the core CSV-export feature
- [x] Wrap `DebugLogFileProvider.exportDebugLogToDownloads()` call in `withContext(Dispatchers.IO)` (SettingsViewModel)
- _Found by the T-018 review (inherited from the original CSV-export reference, not a coder mistake); `./gradlew test` green._

---

## 🟦 Ready
_(empty — Manager promotes a card here once it's unblocked and fully scoped)_

---

## 🔧 In Progress  _(WIP 2/3)_

### T-014 · Repo, Docker & CI setup — US-106
**Owner role:** _devops-engineer_ · Blocked-by: none
- [x] Git repo + `.gitignore` (venv/secrets/keystores/service-account files excluded)
- [x] Flask backend placeholder + pinned `requirements.txt`
- [x] Multi-stage non-root Dockerfile
- [x] Root `docker-compose.yml` (backend + Firestore emulator)
- [x] GitHub Actions CI (lint / test / build / container)
- [x] Validated locally: venv install OK, ruff clean, pytest 3/3
- [ ] Real `docker build` verified (Docker engine wasn't running locally — CI build-image job covers it)
- [ ] GitHub remote created + first push (blocked on `gh auth login -h github.com`)

### T-015 · Emulator-based GPS-route test harness — US-008
**Owner role:** _compliance-qa-specialist + geo-sensors-specialist_ · Blocked-by: T-001 (now exists — unblocked)
- [x] `ANDROID_HOME`/`ANDROID_SDK_ROOT` set to `C:\Android\Sdk`
- [x] `test_device` boots (Android 14 / API 34)
- [x] `adb emu geo fix` GPS injection verified
- [ ] Normal-trip route fixture authored
- [ ] Stop-start-traffic route fixture authored (false-stop / traffic-light test)
- [ ] Park-and-stop route fixture authored
- [ ] Fixtures wired into compliance-qa-specialist test scenarios (brief §10 #1, #2)
- [ ] Actually run against the T-001 app (app now exists, not yet exercised)

---

## 🔍 In Review
_(empty)_

---

## ✅ Done

### T-002 · Vehicle detection (ActivityRecognition) — DONE 2026-06-23 — US-001, US-007
**Owner role:** _geo-sensors-specialist + android-engineer_ · Blocked-by: ~~T-001~~ (Done — unblocked)
- [x] T-002.1 Request the runtime permission _(android-engineer — finished the incomplete shell:_
  _two-step background-location request, real grant-state tracking via `SetupPermissionsPlanner`,_
  _limited-mode banner rule per brief §8, NavHost first-run bug fixed, restricted-settings field_
  _finding investigated and ruled out — full detail in `team/LOGS.md`'s 2026-06-23 11:15 DONE entry)_
- [x] T-002.2 Domain start-event types _(scaffold type + `onStartEvent`; 2 tests added 2026-06-22 chunk 1 → 7/7 green)_
- [x] T-002.3 Register the always-on Transition API trigger _(registrar + receiver bodies + `MT-ActivityRecognition` logging done 2026-06-22 chunk 1; ENTER→gateway seam, EXIT no-op)_
- [x] T-002.4 Specify the confidence-acquisition window _(`ConfidenceAcquisitionWindow`/`Impl` built 2026-06-22 chunk 2; 4 spec §5 virtual-time tests green; bound `@Singleton` — see TASKS.md deviation note)_
- [ ] T-002.5 Specify the optional Bluetooth trigger _(remains deliberately parked/deferred, off-by-default; extended-scope idea — named vehicle profiles + cross-validation logging — parked in TASKS.md, not MVP, NOT part of this Done card)_
- _2026-06-22: geo-sensors-specialist DESIGN pass → `team/blueprints/T-002-vehicle-detection-spec.md`._
- _2026-06-22 chunk 1 (android-engineer): T-002.2 tests + T-002.3 bodies, `./gradlew test` green. T-002.4_
  _seam = `VehicleEntryConfidenceGateway` (no-op binding in `ActivityRecognitionModule`); next chunk swaps in_
  _`ConfidenceAcquisitionWindowImpl` + wires it into the service. Receiver calls `onVehicleEntryDetected(...)` on IN_VEHICLE ENTER._
- _2026-06-22 chunk 2 (android-engineer): `ConfidenceAcquisitionWindowImpl` + `ConfidenceUpdateReceiver` +_
  _`ActivityUpdatesRegistrar` seam built; Hilt rebound from the `NoOp` placeholder; `./gradlew test` 26/26._
- _2026-06-23 (android-engineer, T-002.1 closing chunk): `SetupPermissionsPlanner` (pure Kotlin) +_
  _real `ContextCompat.checkSelfPermission`-backed grant tracking + two-step background-location_
  _request + `StartDestinationViewModel` (fixes the NavHost always-shows-Setup bug) + `MT-UI`_
  _logging. Restricted-settings theory ruled out against Android's actual documented scope (it_
  _gates Accessibility/Notification-Listener/Device-Admin, not standard dangerous permissions);_
  _added an in-app advisory line as a safety net regardless of root cause. `./gradlew test`: debug_
  _59/59, release 59/59, 0 failures/errors (11 test classes, JUnit XML read directly — 3 new classes,_
  _21 new cases). `./gradlew assembleDebug` green. Not committed/pushed — awaiting user go-ahead._
- ✅ _Alongside, not part of T-002 itself: **T-020** (debug-only Bluetooth connection diagnostic logging,_
  _user's own testing only, see `team/TASKS.md`) DONE 2026-06-22 — kept off this board's WIP count_
  _since it's a small dev-tool task riding with T-002's testing, not a separate kanban card. Also fixed_
  _a real leak found along the way: `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` had been speculatively declared_
  _in the main manifest since T-001 — removed; now debug-build-only as intended._

### T-003 · Trip classification flow (Work/Private) — DONE 2026-06-23 — US-002
**Owner role:** _android-engineer_ · Blocked-by: T-001
- [x] T-003.1 Create the notification channel (`mileage_tracker_trip_alerts`, HIGH — built earlier;
  notification now actually `notify()`-ed for the first time, see T-003.3)
- [x] T-003.2 Specify the business-reason validation rule (`ClassificationRules.validateBusinessReason`,
  wired into `TripClassificationViewModel.onSaveClassification` — built earlier, confirmed still correct)
- [x] T-003.3 Specify the lock-screen actionable notification — notification fire wired into the
  single shared `TripTrackingForegroundService.handleStopEvent` tail; `MainActivity.onCreate`/
  `onNewIntent` + new `PendingTripClassificationNavigationStore` nav seam route straight to
  `Screen.TripClassification`; `setShowWhenLocked`/`setTurnScreenOn` wake-on-tap; brief §5.2's
  "must still open the app normally" fallback needs no extra code (Android's own default behavior)
- [x] T-003.4 Wire the pending-business-reason gate (`TripStatus.PENDING_BUSINESS_REASON` — built
  earlier, confirmed still correct)
- _Verified 2026-06-23: `./gradlew test` JUnit XML read directly — debug 38/38, release 38/38, 0
  failures/errors (8 test classes each, incl. new `PendingTripClassificationNavigationStoreTest`,
  7 cases). `./gradlew assembleDebug` green. Not committed/pushed — awaiting explicit user go-ahead._

### T-001 · Scaffold Android project (Kotlin, Compose, Room, Hilt) — DONE 2026-06-22 — underlies all MVP stories
**Owner role:** _android-engineer_ · Blocked-by: none
- [x] T-001.1 Install the toolchain
- [x] T-001.2 Create the Gradle project skeleton
- [x] T-001.3 Configure app/build.gradle.kts
- [x] T-001.4 Declare the manifest
- [x] T-001.5 Build and run the shell (debug APK builds; full screen set already present, beyond the minimum placeholder)
- [x] android-engineer review pass against the blueprint + T-008 signing columns (2026-06-22: conforms; T-008 columns present; tests pass)
- [x] `./gradlew test` actually run and passing (2026-06-22 re-verified: all 5 unit-test classes pass, debug + release; 2026-06-19: `assembleDebug` BUILD SUCCESSFUL via JBR JDK 21; APK installed + launched on emulator-5554, no crash)
- [x] ⚠️ HIGH blocker FIXED 2026-06-22 — service now driven by `TripLifecycleStateMachine` (start-side transient-phase resolution + stop-side `TripStatus`); state machine is no longer dead code; all tests still green
- [x] ⚠️ MEDIUM blocker FIXED 2026-06-22 — `service/di/ServiceModule.kt` created per blueprint §3
- [x] Committed to git + pushed to public remote (commit `c846b05`, 2026-06-22, `origin/main`)
- _✅ T-001 fully DONE 2026-06-22 — both review blockers were already fixed in `c846b05` (the T-018 pass,
  same files). Card relocated here from In Progress 2026-06-22 after an android-engineer fix-verify pass
  re-confirmed it via a clean `./gradlew clean test` (BUILD SUCCESSFUL, 62 tasks, all 5 test classes green,
  debug + release) — no new source changes needed (working tree clean)._
- ℹ️ Deferred to T-002 (not blocking): ActivityRecognition auto-start is registered but not yet feeding
  `TripStartEvent.ConfidentVehicleEntry` into the state machine (tagged TODO in the service class doc).

### T-008 · Cryptographic trip signing (tamper-evident logbook) — design decided 2026-06-18 — US-101
**Owner role:** _security-crypto-specialist_ · `/team-debate`, 2 rounds + cost ruling
- [x] Per-trip ECDSA P-256 signature (Android Keystore, StrongBox→TEE fallback)
- [x] Rolling tail-hash chain in one-row DataStore entry (not a per-row column)
- [x] Two new nullable `TripEntity` columns: `signatureBase64`, `signingKeyId`
- [x] Canonical signed-field list + fixed-precision serialization rules
- [x] Write-order/durability resolved (Room = durability anchor, DataStore tail = derived cache)
- [x] Cost ruling: APPROVE ($0 MVP impact)
- _(the design is what's Done here — the actual implementation steps, T-008.1–T-008.5, are listed
  under T-006's Backlog card above and get checked off there, not here, when T-006 executes them)_

### T-021 · All-in-one dev environment startup script — DONE 2026-06-22
**Owner role:** _devops-engineer_ · Blocked-by: none · added 2026-06-22
- [x] `scripts/start-dev.ps1` — idempotent backend `.venv` + Flask check/start
- [x] Idempotent Android emulator check/boot (`test_device`, 90s boot-completed poll)
- [x] Always-run `gradlew installDebug` reload + `adb shell am start` launch
- [x] Bonus `-Stop` switch (only stops what the script itself started)
- _Verified end-to-end: cold start, immediate re-run (correctly skips already-running pieces), `-Stop`._
- _Not committed yet — riding with T-002.4/T-020, awaiting explicit go-ahead._

### T-010 · Backend cost model — ruling delivered 2026-06-18
**Owner role:** _cost-architect_
- [x] Per-user/month projections at 20 / 1k / 10k users (Cloud Run + Firestore + Storage + ML)
- [x] Free-tier-safe defaults identified (batched writes, photo opt-in OFF-default, `min-instances=0`, on-device OCR)
- [x] Binding constraint identified: Firestore 20k writes/day
- [x] Ruling: APPROVE-WITH-CHANGES (5 required changes specified in `team/blueprints/T-010-backend-cost-model.md`)
- [ ] _(carried forward, not blocking)_ re-run projections with real telemetry before scaling past closed testing

---

> Move a card by cutting its whole block and pasting it under the new column heading, then update
> "Last moved" and add a LOGS.md note. Never delete a card, a checkbox, or the **Owner role** line —
> strike it through if cancelled. Check a box in place as sub-work finishes; only move columns when
> the card's overall status actually changes.
