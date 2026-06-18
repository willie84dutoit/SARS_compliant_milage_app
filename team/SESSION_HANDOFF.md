# Session Handoff — Automated Mileage Tracker

> Written by the Manager at the end of a working session (via `/handoff`) and read at the
> start of the next one. **Append a new dated section each session; never delete old ones.**
> ~~Purpose: a cold-start summary so the next session resumes with full context.~~
>
> **Purpose (corrected 2026-06-18): this is a NARRATIVE CARRY-OVER of the actual conversation, not a status tracker.**
> Capture the discussions, the reasoning behind choices, the asides and "by the way" moments, the things
> the user said they want, half-finished trains of thought, and anything that would be lost if the chat
> vanished. Status/where-tasks-stand lives in `TASKS.md` and `LOGS.md` — do NOT duplicate that here.
> If in doubt: would a new session lose important *context or intent* without this note? Then write it down.

## How to use
- **Start of session:** read the most recent section below (for context/intent) + `team/TASKS.md` (for status) + the tail of `team/LOGS.md` (for decisions).
- **End of session:** the Manager runs `/handoff`, which appends a new section here capturing the conversation's carry-over.

---

## Session 2026-06-18 — Workflow bootstrap

### State of the project
- Specs only; no code yet. Source docs:
  `automated_mileage_tracker_spec.md`, `developer_handoff_brief.md`,
  `post_mvp_api_plan.md`, `publisheing guide.md`.
- MVP target: local-first Android (Kotlin/Compose/Room/Hilt), 2-week plan.

### What happened this session
- Stood up the multi-agent team (Manager + 9 specialists), orchestration skills, and
  the LOGS / TASKS / SESSION_HANDOFF tracking files.

### Open decisions / debates
- T-008 crypto signing scheme not yet decided (hash-chain vs. per-trip signature).
- T-010 backend cost model not yet produced.

### Next actions
- User to direct the Manager toward the first MVP milestone (likely T-001 scaffold).

### Watch-outs (carried risks)
- ActivityRecognition reliability varies by device.
- False-positive / false-stop detection is the highest-risk area (per spec §13).
- Background-location store approval requires prominent disclosure + demo video.

---

## Session 2026-06-18 (part 2) — Tooling, environment & GCP setup; app still not started

### The through-line of the conversation
This whole session was **setup, not building**. The user repeatedly steered toward "is the
environment ready / am I safe" rather than writing app code. We ended explicitly agreeing: **the app
has NOT been started** — T-001 is the first line of real product code. Don't let a future session
mistake the scaffolding for progress on the app itself.

### What we discussed and decided (the carry-over, not just status)
- **Team grew during the session by user request:** added 3 Haiku **coders** (`android-coder`,
  `backend-coder`, `general-coder`) specifically to *save tokens* on routine implementation, and added
  a `devops-engineer`. Tiered model strategy is deliberate: opus/sonnet specialists design+review,
  Haiku coders implement. The user cares a lot about token/£ cost — keep leaning cheap.
- **"I only talk to the main window"** — confirmed twice. The user wants a single point of contact and
  was initially unsure the agents/folders were real. Lesson: when the user can't *see* something
  (`.claude` is hidden), show them on disk and give a visible index (`team/README.md`).
- **Folders:** user wanted top-level `agile/` and `userstories/` (not nested under `team/`, not named
  "kanban"). Moved them. They like things where they can see and read them.
- **Model question (still open-ish):** I'm currently running **Opus**. I recommended running the
  Manager on **Sonnet** day-to-day (agents keep their own fixed models) to save cost, switching to
  Opus only for big debates/architecture. User hasn't switched yet — offer again next session.
- **Emulator saga (resolved):** big time-sink. Root cause was a *wrong SDK path* — I'd set
  `ANDROID_HOME` to the empty `%LOCALAPPDATA%\Android\Sdk`. The real, populated SDK is **`C:\Android\Sdk`**
  (Android Studio at `C:\Android Studio`), which the user's *other* app uses. After re-pointing,
  `test_device` boots (Android 14 / API 34) and `adb emu geo fix` GPS injection works. **Brittleness
  flagged:** SDK path + AVD name are machine-specific. The user has multiple projects/Claudes on this
  machine — watch for cross-project config bleed.
- **GCP (resolved + a standing rule):** user saw the VS Code Cloud Code status bar showing
  `indoorstockcontrol-498411` (another app) and worried about deploying to the wrong project. We created
  a dedicated project **`mileage-tracker-716601`** and an isolated gcloud config **`milage-app`**
  (underscores aren't allowed, hence the hyphen; user typed "milage_app"). **Billing intentionally NOT
  linked** — user agreed "we won't link it yet." Standing intent: link billing only after the T-010
  cost model, and **do not reuse another app's billing account** (e.g. LGG_Indoor_Stock).
- **New working rules the user asked for (now in CLAUDE.md):** always keep TASKS (or equivalent)
  updated; always write this handoff at session end; and this handoff is a *discussion carry-over*,
  not a status tracker.

### Open threads to pick up
- **GitHub push still not done** — blocked on the user running `gh auth login -h github.com` (token
  expired). Once authed, run: `gh repo create mileage-tracker --private --source=. --remote=origin --push`.
  Repo is committed locally on `main`; no remote yet.
- **Token-budget plan — DECIDED: Option A is the next task.** ~~User hadn't explicitly picked A/B/C. Re-offer.~~
  User chose **Option A**: do the design/decision work (no build risk) rather than scaffold code and
  risk Gradle build-loops eating the budget. Full description below.
- **Manager model:** still on Opus; offer Sonnet switch for cost.

### THE NEXT TASK — Option A: a design & decisions sprint (no code, no builds)

The next task is **not** to write scaffold code yet. It is to turn the paper specs into an
**execution-ready plan**, so a later (full-window) session can scaffold and build fast and cheap.
This is build-light, bounded, and durable — zero risk of Gradle build-loops burning the budget.

**Option A consists of three pieces (do in this order; the blueprint is the must-have):**

1. **T-001 Android architecture blueprint** *(owner: `android-engineer`, must-have)* — a written spec,
   NOT compiled code:
   - The Gradle module/layer layout for ui / domain / data / service (per brief §6.1 boundaries).
   - The Room `Trip` entity (fields per brief §6) + DAO method signatures + the `trip_photo` table.
   - The Hilt dependency-injection graph (what's provided where).
   - The trip-lifecycle **state machine** (active → pending_business_reason / pending_ocr → completed),
     honouring the locked v1 thresholds (70% start, 3-min stop, 2-min unstable, 30s prompt, 10m filter).
   - The Compose screen list (the 7 screens in brief §7) and the ViewModel per screen.
   - A concrete **build order** for the eventual scaffold (what gets created first, what depends on what),
     and exactly which slices get handed to `android-coder` (Haiku) vs decided by `android-engineer`.

2. **T-010 backend cost model** *(owner: `cost-architect`, recommended)* — per-user/month projection at
   20 / 1k / 10k users for Cloud Run + Firestore + GCP Storage + on-device vs cloud ML; free-tier-safe
   defaults. This is what gates the GCP billing-link decision (project `mileage-tracker-716601`).

3. **T-008 crypto-signing debate** *(owner: `security-crypto-specialist`, optional if budget allows)* —
   run `/team-debate`: per-trip signature vs. hash-chain for the tamper-evident SARS logbook; pick the
   scheme, the covered canonical fields, and key storage (Android Keystore). Logged decision.

**Definition of done for Option A:** the blueprint exists and is detailed enough that scaffolding
becomes mechanical; (if reached) the cost model and signing decision are logged. Then `/handoff` again.

**Suggested next-session opening request:**
> "Run Option A from the handoff — start with the T-001 architecture blueprint from android-engineer,
> then the T-010 cost model, then the T-008 signing debate if budget allows. Design only, no scaffold code yet."

~~"Start T-001 — scaffold the Android project ... Let's start T-001."~~ (superseded: scaffolding code
comes AFTER the blueprint, in a fresh full window.)

### Practical environment notes for next session
- Active gcloud config is `milage-app` → project `mileage-tracker-716601`, account willie84dutoit@gmail.com.
  To work on other apps: `gcloud config configurations activate <name>` (e.g. `images-duplicate-finder`).
- Android: `ANDROID_HOME` = `C:\Android\Sdk`; AVD `test_device` boots; emulator may still be running.
- **Build toolchain unverified:** we have NOT confirmed a JDK + Gradle for the Android build. Cheap
  first check before scaffolding T-001, so build-loops don't surprise us.
