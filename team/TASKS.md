# Task Board — Automated Mileage Tracker

> Maintained by the Manager. Tasks move down the board; **completed tasks are never deleted**
> (per global no-delete rule) — they move to "Done" with a completion date.
> Each task has a stable id (`T-001`, `T-002`, …) referenced from LOGS.md.

## Conventions
- **Owner:** which specialist agent is responsible.
- **Phase:** MVP (Android) | Phase-2 (Backend/API) | Phase-3 (iOS) | Phase-4 (Huawei) | Cross-cutting.
- **Blocked-by:** task ids this depends on.
- **Status:** Backlog → Ready → In progress → In review → Done (or Parked).

---

## In Progress
_(none yet)_

## Ready
_(none yet)_

## Backlog

### T-001 · Scaffold Android project (Kotlin, Compose, Room, Hilt)
- **Owner:** android-engineer · **Phase:** MVP · **Blocked-by:** none
- Set up Gradle, module layout (ui / domain / data / service), Hilt DI graph, Room base.

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

### T-008 · Cryptographic trip signing (tamper-evident logbook)
- **Owner:** security-crypto-specialist · **Phase:** Cross-cutting · **Blocked-by:** T-006
- Decide signing scheme (per-trip hash chain vs. signature), key storage (Keystore), SARS audit value.

### T-009 · Backend sync API (Flask on Cloud Run + Firestore)
- **Owner:** backend-engineer · **Phase:** Phase-2 · **Blocked-by:** T-006, T-008
- Idempotent upload endpoint, Firestore security rules, GCP Storage for photos.

### T-010 · Cost model for backend (Cloud Run / Firestore / Storage / ML)
- **Owner:** cost-architect · **Phase:** Phase-2 · **Blocked-by:** none
- Per-user/month cost projection at 20, 1k, 10k users; recommend free-tier-safe defaults.

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
