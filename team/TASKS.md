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

### T-014 · Repo, Docker & CI setup (GitHub + .venv + Cloud Run image)
- **Owner:** devops-engineer · **Phase:** Cross-cutting · **Blocked-by:** none
- Git repo + `.gitignore` (ignore `.venv/`, secrets, keystores, `google-services.json`, `serviceAccount*.json`),
  backend Dockerfile + docker-compose (with Firestore emulator), Python `.venv` workflow + pinned deps,
  GitHub Actions CI (lint/test/build/container). Foundation: `.gitignore` + `git init` done 2026-06-18.

---

## Done
_(none yet — completed tasks move here with a `✅ done YYYY-MM-DD` tag)_
