# Team Logs — Automated Mileage Tracker

> **Append-only.** Never edit or delete past entries (per global no-delete rule).
> To correct something, add a new entry that supersedes the old one and reference it.
> The Manager writes here after every meaningful action: decisions, debates, delegations,
> cost rulings, blockers, and completed work.

## Entry Format

```
### [YYYY-MM-DD HH:MM] <TYPE> — <short title>
- **Actor:** manager | <agent-name>
- **Context:** what prompted this
- **Action / Decision:** what was decided or done
- **Rationale:** why
- **Cost impact:** (if any) <cost-architect ruling or "n/a">
- **Follow-up:** linked TASKS.md ids, or "none"
```

TYPE is one of: DECISION, DEBATE, DELEGATION, COST, BLOCKER, DONE, NOTE.

---

## Log

### [2026-06-18 12:30] NOTE — Team workflow initialised
- **Actor:** manager
- **Context:** User requested a manager-fronted multi-agent team for the mileage tracker.
- **Action / Decision:** Created specialist agents, orchestration skills, and the LOGS / TASKS / SESSION_HANDOFF tracking files. User communicates only with the Manager.
- **Rationale:** Single point of contact; specialists debate and analyse; cost-architect gates spend.
- **Cost impact:** n/a (tooling only)
- **Follow-up:** see TASKS.md

### [2026-06-18 13:45] NOTE — Planning folders moved to top level
- **Actor:** manager
- **Context:** User wanted the agile board and user stories as top-level folders.
- **Action / Decision:** `git mv team/kanban → agile`, `git mv team/user-stories → userstories`; fixed all cross-references in CLAUDE.md, team/README.md, and the moved files.
- **Rationale:** Match the user's expected layout; keep stories/board easy to find.
- **Cost impact:** n/a
- **Follow-up:** none

### [2026-06-18 13:50] DELEGATION — T-014 DevOps foundation (Docker + .venv + CI)
- **Actor:** manager → devops-engineer
- **Context:** User asked to build the Docker/.venv foundation now, before backend code exists (US-106).
- **Action / Decision:** Delegated; devops-engineer created `backend/` (placeholder Flask app, pinned requirements, multi-stage non-root Dockerfile, pytest, ruff config, README with .venv workflow), root `docker-compose.yml` (backend + Firestore emulator), and `.github/workflows/ci.yml` (lint+test, then image build).
- **Rationale:** Reproducible, Cloud Run-ready repo from day one.
- **Cost impact:** n/a now (local/CI files only). The actual Cloud Run **deploy** must clear cost-architect (see T-009/T-010).
- **Follow-up:** T-014 — validated locally (venv install OK, ruff clean, pytest 3/3). Open: real `docker build` (Docker engine not running locally; CI build-image job will cover it) and GitHub remote + push.

### [2026-06-18 14:10] DONE/PARTIAL — Android emulator env wired (T-015 part 1)
- **Actor:** manager
- **Context:** User asked to wire the Android emulator for testing (option b).
- **Action / Decision:** Set `ANDROID_HOME` + `ANDROID_SDK_ROOT` (User scope) to `%LOCALAPPDATA%\Android\Sdk`; added `platform-tools` + `emulator` to User PATH. ~~booted AVD `test_device` (android-36) and confirmed `sys.boot_completed`~~. Opened T-015 / US-008 for emulator-based GPS-route testing.
- **Rationale:** The emulator can replay GPS routes (GPX/KML / `adb emu geo fix`) to test the highest-risk feature — trip-start and false-stop detection — without driving.
- **Cost impact:** n/a (local tooling)
- **Follow-up:** T-015 — author route fixtures; needs the app from T-001 to drive against.

### [2026-06-18 14:20] BLOCKER — test_device cannot boot (correction to 14:10)
- **Actor:** manager
- **Context:** Attempted to boot `test_device`; correcting the 14:10 entry which wrongly claimed boot was confirmed — it was not.
- **Action / Decision:** Emulator panicked: `Cannot find AVD system path`. Root cause: `test_device`'s `config.ini` requires `system-images/android-34/google_apis/x86_64/`, which is NOT installed; and `cmdline-tools` (sdkmanager/avdmanager) is absent, so no image can be fetched via CLI yet.
- **Rationale:** Honest record — env vars are set, but no bootable AVD exists.
- **Cost impact:** Fix needs ~150 MB (cmdline-tools) + ~1.2 GB (system image) download. 230 GB free, so disk is fine.
- **Follow-up:** Awaiting user decision: install cmdline-tools + android-34 google_apis x86_64 image via CLI, or fix via Android Studio's GUI SDK Manager. T-015 part 1 stays OPEN until an AVD boots.

### [2026-06-18 14:25] DECISION — Fix emulator via Android Studio GUI (user-owned)
- **Actor:** manager (user choice)
- **Context:** Two fix paths offered for the missing system image.
- **Action / Decision:** User will install a system image via Android Studio's SDK/Device Manager. To boot the existing `test_device` unchanged, install **API 34 (Android 14) · Google APIs · x86_64**. Manager to verify boot afterwards via `adb`.
- **Rationale:** Path of least resistance if Android Studio is installed; matches `test_device` config.
- **Cost impact:** local download only.
- **Follow-up:** T-015 stays blocked until the user reports the image is installed; then Manager runs the boot check.

### [2026-06-18 14:45] DONE — Emulator boot confirmed; blocker resolved (T-015 part 1)
- **Actor:** manager
- **Context:** Other Claude confirmed the working SDK is `C:\Android\Sdk` (Android Studio at `C:\Android Studio`). The 14:20 blocker was a wrong SDK path, not a missing image.
- **Action / Decision:** Re-pointed `ANDROID_HOME`/`ANDROID_SDK_ROOT` (User scope) from the empty `%LOCALAPPDATA%\Android\Sdk` to `C:\Android\Sdk`; fixed PATH. Booted `test_device`: **boot_completed in ~35s, Android 14 (API 34), `emulator-5554`**. Verified `adb emu geo fix` returns OK (GPS route injection works).
- **Rationale:** Real, working location-injection path for testing trip-start / false-stop detection without driving.
- **Cost impact:** n/a (used the already-installed SDK).
- **Follow-up:** T-015 part 1 complete. Open: route fixtures + the app from T-001. NOTE: SDK path/AVD name are machine-specific (brittle across machines) — revisit if portability matters.

### [2026-06-18 15:10] DECISION — Dedicated GCP project + isolated gcloud config; billing deferred
- **Actor:** manager (user-approved)
- **Context:** User wanted to avoid deploying to the wrong project (`indoorstockcontrol-498411` belonged to another app).
- **Action / Decision:** Created GCP project **`mileage-tracker-716601`** (display "Mileage Tracker"); created and activated isolated gcloud configuration **`milage-app`** (account willie84dutoit@gmail.com, project mileage-tracker-716601). Note: underscores invalid in project IDs / config names, so config is `milage-app` not `milage_app`.
- **Rationale:** Isolates this app's cloud context from the user's other projects; prevents wrong-project deploys.
- **Cost impact:** $0 — project creation is free; **billing intentionally NOT linked** (`billingEnabled:false`). Linking is gated by `cost-architect` and should follow T-010 (cost model). Do not reuse another app's billing account (e.g. LGG_Indoor_Stock).
- **Follow-up:** Before any deploy: run T-010 cost model → choose/confirm billing account → `gcloud billing projects link mileage-tracker-716601 --billing-account=<ID>`. To switch gcloud back to other work: `gcloud config configurations activate <name>`.
