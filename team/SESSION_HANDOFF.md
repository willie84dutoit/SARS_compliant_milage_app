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

<!-- REDACTED 2026-06-19: personal Gmail address and the real GCP project id / gcloud config name
were replaced below with `<redacted-personal-email>` / `<redacted-gcp-project-id>` /
`<redacted-gcloud-config>` before this repo went public (open-source portfolio decision +
pre-push leak-check rule in .claude/CLAUDE.md). The real values are not reproduced here. -->

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
  a dedicated project **`<redacted-gcp-project-id>`** and an isolated gcloud config **`<redacted-gcloud-config>`**
  (underscores aren't allowed in project ids / config names, hence the hyphen). **Billing intentionally NOT
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
   defaults. This is what gates the GCP billing-link decision (project `<redacted-gcp-project-id>`).

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
- Active gcloud config is `<redacted-gcloud-config>` → project `<redacted-gcp-project-id>`, account `<redacted-personal-email>`.
  To work on other apps: `gcloud config configurations activate <name>`.
- Android: `ANDROID_HOME` = `C:\Android\Sdk`; AVD `test_device` boots; emulator may still be running.
- **Build toolchain unverified:** we have NOT confirmed a JDK + Gradle for the Android build. Cheap
  first check before scaffolding T-001, so build-loops don't surprise us.

---

## Session 2026-06-18 (part 3) — Option A executed in full: blueprint + cost model + signing decision

### The through-line of the conversation
The user opened with exactly the suggested request from the part-2 handoff, verbatim: run Option A,
in priority order, design only, no scaffold code. That's exactly what happened, in one session,
without touching a single Gradle/Kotlin file — the build-loop risk the user wanted to avoid all of
last session stayed at zero. The Manager model question from part 2 resolved itself quietly: the
session started on `/model sonnet`, so we're now running the Manager on Sonnet day-to-day as
recommended, not Opus.

### What we discussed and decided (the carry-over, not just status)
- **T-001 and T-010 ran in parallel** (both `Blocked-by: none` for each other) — `android-engineer`
  and `cost-architect` spawned in one batch. `android-engineer` has Write/Edit tools and persisted
  its own blueprint + updated TASKS.md/LOGS.md directly (you'll see its edits attributed to itself,
  not the Manager, if you read the raw logs — that's expected, not a glitch). `cost-architect` has
  no Write tool, so the Manager persisted its report to `team/blueprints/T-010-backend-cost-model.md`
  by hand. Both verified against the brief before being accepted as done.
- **T-008 was the real work of this session** and is a good example of the `/team-debate` procedure
  working as designed, worth remembering for future debates: Round 1 produced a *genuine* clash, not
  a manufactured one. `security-crypto-specialist` wanted a per-row `previousTripHash` column on
  `TripEntity`; `android-engineer` rejected it on three concrete grounds (forces a read-then-write
  transaction that doesn't exist anywhere else in the DAO; "previous trip" is ambiguous because trips
  finalize out of start-time order — a Work trip can sit in `pending_business_reason` for days while
  later trips complete; a crash mid-write leaves an unrepairable chain gap) and offered a narrower
  fallback — a rolling tail-hash in a one-row DataStore entry instead of a per-row column. That was
  exactly the kind of fact-clash the debate skill says deserves a Round 2, so we ran one: gave each
  side the other's argument, and both converged — security-crypto-specialist withdrew the per-row
  column and accepted the DataStore design (confirming, when pushed, that deletion-detection works
  identically either way — the guarantee comes from the chain existing, not from where it's stored);
  android-engineer accepted the converged design but surfaced one last split (does Room write first
  or does DataStore write first?), each side citing a different self-heal direction. That last point
  is the one thing neither specialist closed — the Manager resolved it directly from their own
  reasoning rather than spawning a third round: make Room the sole durability anchor and treat
  DataStore's tail as a derived cache, rebuilt from the last signed Room trip on every cold start.
  That dissolves the ordering question because a crash either way self-heals from Room alone.
  **Final scheme:** per-trip ECDSA P-256 signature (Android Keystore) + a rolling tail-hash chain
  (not a per-row column) + two new nullable `TripEntity` columns (`signatureBase64`, `signingKeyId`).
  Full detail in `team/LOGS.md`'s `[2026-06-18 17:10] DECISION` entry and
  `team/blueprints/T-001-android-architecture-blueprint.md`'s open-question §3.
- **cost-architect's T-008 ruling surfaced one NEW thread that isn't on the task board yet:**
  because the chain tail lives in per-device DataStore, a device wipe / reinstall / second device
  starts a *new* chain from genesis. cost-architect flagged this as a verification-semantics
  question for whoever eventually builds T-009 (the backend must treat chain segments as
  per-`signingKeyId`, not assume one continuous chain per user-account) — it's noted in the cost
  ruling but deliberately **not** turned into its own task yet since T-009 hasn't started. Don't
  lose this when T-009 actually opens.
- **The GCP billing-link decision is analytically unblocked but not executed.** T-010's
  APPROVE-WITH-CHANGES ruling (from part 2/session 3) plus this session's confirmation that T-008
  adds zero MVP cost means there's no remaining cost-model reason to keep billing unlinked. But
  actually running `gcloud billing projects link <redacted-gcp-project-id> --billing-account=<ID>` is a
  real-world action with real billing consequences on the user's account — the Manager should
  confirm with the user before doing it, not treat "the model says it's safe" as authorization to
  act. Surface this as a choice next session, don't just do it.

### Open threads to pick up
- **GitHub push still not done** — same as part 2, still blocked on `gh auth login -h github.com`
  (token expired). Once authed: `gh repo create mileage-tracker --private --source=. --remote=origin --push`.
- **Billing-link decision** — see above; needs an explicit user go-ahead, not an assumed one.
- **Build toolchain still unverified** (JDK + Gradle for the Android build) — still nobody has
  checked this. It's the cheap first move before any real scaffolding starts, so a build-loop
  doesn't eat the budget on day one of actual coding.
- **T-009 (backend sync API)** is the next thing that's actually blocked-by completed work
  (T-006 + T-008, both now resolved at the design level) — but T-006 itself (the Room persistence
  layer) hasn't been scaffolded yet, so T-009 is still a Phase-2-away concern, not a now concern.

### The agreed next request / next step
**Option A is fully done** — blueprint, cost model, and signing decision are all logged and
cross-referenced. The natural next session is the one Option A was explicitly designed to set up:
**scaffold T-001** (actual Gradle/Kotlin project creation, in a fresh full context window, following
the build order in `team/blueprints/T-001-android-architecture-blueprint.md` §6 — which now also
needs the T-008 signing columns folded into step 3's Room schema rather than retrofitted later).
Before that starts, it's worth a 2-minute JDK/Gradle toolchain check so the first scaffold attempt
doesn't immediately hit a build-loop.

### Practical environment notes for next session
- Same as part 2: `ANDROID_HOME` = `C:\Android\Sdk`; gcloud config `<redacted-gcloud-config>` → project
  `<redacted-gcp-project-id>`, billing still NOT linked (pending the user go-ahead noted above).
- Manager is running on Sonnet (`/model sonnet` was set at the start of this session) — keep it
  there for day-to-day work per the part-2 cost recommendation; switch to Opus only for a big
  debate/architecture session if one comes up.
- No Kotlin/Gradle/Python files were touched this session — `team/` markdown and
  `team/blueprints/*.md` only. Zero build risk carried into next session.

---

## Session 2026-06-19 (part 4) — Found an unlogged built scaffold, then a long planning-document iteration, then a real scope correction at the very end

### The through-line of the conversation
This session opened with `/standup` and immediately surfaced a real problem: `team/TASKS.md` said
"T-001 scaffold not started," but the filesystem had a full, **built** Android app sitting
untracked (debug APK present, all 7 screens, domain/data/service layers per the blueprint) —
nobody had updated the board for it. After confirming with the user that they'd commit it
themselves (and reconfirming the standing "Manager never commits without an explicit go-ahead"
rule), the rest of the session became an extended back-and-forth over **how detailed the planning
docs should be**, ending in a real **scope correction**: the user's actual goal is a `.apk`
sideloaded on their own phone, not a published/multi-platform product — which retroactively makes
a chunk of this session's planning work lower-priority than it was treated as while writing it.

### What we discussed and decided (the carry-over, not just status)
- **The planning-doc iteration had a real arc worth remembering for next time**, because it
  repeatedly overshot and undershot before landing:
  1. User: "full full full plan, every feature, every task" → rebuilt `agile/board.md` with a card
     per `T-###` task and a feature checklist per card (16 tasks, none skipped).
  2. User: "not enough... READ THE .MD FILES... TASK & HOW IT WILL BE DONE" → wrote
     `team/blueprints/FULL_IMPLEMENTATION_PLAN.md` as prose architecture summaries per task,
     explicitly flagging 4 real open technical questions rather than guessing them.
  3. User escalated hard: "ANY JUNIOR DEVELOPER... they should not have to think of anything...
     A COMPLETE PLAN!!" → this was read as "close the open questions for real," so two specialist
     agents were spawned: **geo-sensors-specialist** ruled on T-002 (how to actually derive a 70%
     confidence value from the Activity Recognition Transition API, which has no confidence field
     — answer: a short-lived parallel `requestActivityUpdates()` window, max-so-far confidence) and
     T-004 (GPS noise floor = 8.0m; Room distance writes batched every 15s, not per-update); **ml-
     ocr-specialist** ruled on T-005 (ML Kit's on-device API has no confidence field either — answer:
     a composite 4-signal weighted score — digit-count/isolation/position/structural-purity —
     standing in for it, gated at 80). These rulings are good, real, and already folded into the
     current plan — they don't need to be redone.
  4. Mid-delegation, the user shut it down hard: **"NOOOOOOOOOO... forget about agents and shit"**
     and gave an explicit format instead: numbered `T-001.1`, `T-001.2`... micro-steps with a literal
     "How," plus "work on the TDD." The third specialist delegation (analytics taxonomy, T-011) was
     cancelled and decided directly instead.
  5. First rewrite in that format embedded full Kotlin/Python implementations per step. User:
     **"i never said generate code... i said specify the tasks"** + "and how to accomplish..." →
     rewrote again to be spec-only (exact API/class/method/config names and exact decided constants,
     no implementation bodies) and added an explicit **Verify** line to every single micro-step.
  6. User: **"i dont see any front end tasks"** → genuinely true gap, added **T-017** (Compose UI
     screens — nav graph, theme, layout for all 7 screens, loading/empty/error states, a11y pass) to
     `TASKS.md`, `agile/board.md`, and the plan, in the same format as everything else.
  7. User: "I need a kanban board please" — this read ambiguous (markdown vs. a real tool), asked
     via `AskUserQuestion`, user picked "just fix it according to the plan — don't delete the
     roleplayer." **"Roleplayer"** was interpreted as the owner/specialist-role line on each card —
     rebuilt `agile/board.md` so every checkbox is the literal `T-XXX.Y` step from the plan (1:1
     traceable), with the owner line preserved and relabeled `**Owner role:**` plus an explicit
     no-delete note protecting it.
  - **Lesson for next session on this user's working style:** they want action, not repeated
    clarifying questions, once intent is reasonably inferable — multiple all-caps/profanity
    messages in a row were frustration at overshoot/undershoot, not malice, and the fix each time
    was "do less talking, more correcting in the actual file." But genuine forks (e.g. "markdown
    board vs. a real tool") were fine to ask about. Calibrate: ask when the fork is real and
    cheap-to-answer; don't ask when the correction is already obvious from what they just said.
  - **Tooling note:** `AskUserQuestion`'s "Other" custom-text option did not come through with
    actual text once this session (the answer value was literally the string `"Other"`) — had to
    ask the user directly in chat to get the real answer. Worth double-checking if this recurs.

- **The real scope correction, right at the end — this is the most important thing to act on next
  session.** User asked "what is the mvp" and, after I gave the documented MVP list, clarified:
  *"i think i said published earlier, but a .apk on my phone is fine."* This means the actual goal
  is a **personal, sideloaded APK** — not a Play Store / App Store / AppGallery release, not
  multi-user cloud sync. That makes several already-planned tasks genuinely lower-priority or
  unnecessary for what the user actually wants right now:
  - **T-012** (store publishing readiness) — not needed at all for a sideloaded APK.
  - **T-009/T-011** (backend sync, analytics/SARS reporting) — only matter with other users/a
    backend; not needed for one person's own device.
  - **T-013/T-016** (iOS, Huawei) — other-platform work, not needed.
  - Real MVP-for-this-actual-goal is just **T-001–T-008 + T-017**, build → `./gradlew assembleDebug`
    → `adb install`.
  - **T-008 (signing) is worth re-asking about, not assuming** — it exists for SARS audit
    defensibility; if this is purely personal use rather than a tax claim, it may not be needed
    either, but the user hasn't said that — don't drop it without asking.

### Open threads to pick up
- **The actual TASKS.md "Parked" edit was never completed.** I started annotating
  T-011/T-012/T-013 as `⛔ Parked 2026-06-19` with reasoning, the user rejected that exact tool call
  mid-edit, and then immediately asked for this handoff instead — so **T-009, T-011, T-012, T-013,
  T-016 are still shown at full Backlog priority on both `team/TASKS.md` and `agile/board.md`**,
  contradicting the scope the user just confirmed. This is unresolved, not done. It's unclear
  whether the rejection meant "don't park them," "park them but let me write the reasoning," or
  "just pause so we can handoff first" — **ask before re-attempting**, don't assume the original
  parking plan is still wanted verbatim.
- Whether **T-008** (signing) still belongs in the real MVP scope given "personal APK only" — flagged
  above, not decided.
- GitHub push still not done (same long-standing blocker — `gh auth login -h github.com`).
- Billing-link decision still pending explicit user go-ahead (unchanged from earlier sessions).
- The scaffold found on disk at the top of this session is still uncommitted (user said they'll
  commit it themselves) and still unreviewed against the blueprint (no `./gradlew test` run yet).

### The agreed next request / next step
Resolve the open Parked-status question first (ask the user plainly: park T-009/011/012/013/016 or
not, and what about T-008), then actually edit `TASKS.md`/`board.md` to match whatever they say.
After that, the natural next step — given the user's actual goal is just an APK on their phone — is
likely to stop planning and start *building*: review the existing scaffold against the blueprint,
run the test suite, and start working T-002 onward for real, using
`team/blueprints/FULL_IMPLEMENTATION_PLAN.md` as the build guide.

### Practical environment notes for next session
- Same as part 3: `ANDROID_HOME` = `C:\Android\Sdk`; gcloud config/project still as before, billing
  still not linked.
- Manager still on Sonnet.
- This session touched only `team/`, `agile/`, and `userstories/` markdown — no Kotlin/Gradle/Python
  files were edited (the existing scaffold was read/audited, not modified). Zero build risk carried
  into next session.
- New artifacts this session: `team/blueprints/FULL_IMPLEMENTATION_PLAN.md` (the build guide, now in
  its corrected spec+Verify form, including T-017), `userstories/epic-03-platforms-publishing.md`
  (US-201–US-204), US-107 added to epic-02, T-016/T-017 added to `TASKS.md`.

---

## Session 2026-06-22 (part 5) — Verified the foundation, hardened it for field testing, first real Haiku-coder run, then shipped it to a public repo

### The through-line of the conversation
This session turned the corner from *planning* to *verified, committed product*. We opened by closing
the two forks the part-4 handoff left hanging, then did the verification pass that part-4 said was the
real next step — which immediately paid off by catching a cracked foundation (the service bypassing the
tested state machine) before any features were built on it. The back half became a real-world git
hygiene exercise: committing the whole MVP scaffold to a brand-new **public** GitHub repo, with the
user rapidly course-correcting the remote URL, the commit identity, and `.gitignore` as we went. The
single most important new fact for every future session: **there is now a hard deadline — a working
MVP (personal sideloaded APK) by Friday afternoon, framed as "the next ~10 sessions."** Pace
accordingly (~1 real task/session); that's why we deliberately spent this session hardening the
foundation rather than racing into features on top of a flaw.

### What we discussed and decided (the carry-over, not just status)
- **The two part-4 forks are now CLOSED, both in favour of keeping scope wide:** the user chose to
  **NOT park** T-009/T-011/T-012/T-013/T-016 (the multi-platform/published-product roadmap stays
  active on the board even though the immediate target is a personal APK) and to **keep T-008**
  (signing) in the MVP as cheap tax-defensibility insurance. So the "should we park these?" thread
  that's been carried since part-4 should not be re-raised — it's settled.
- **New, load-bearing intent — field testing drives the quality bar.** The user said the app will go
  to human field testers, and an emulator test isn't the final decider — a real field test is. From
  that they asked, unprompted, for **proper logging + try/catch error handling** so that when a tester
  hits a problem in the wild, they can hand back a log file and the Manager can diagnose it. This is
  *why* T-018 exists. The mental model to preserve: a failure in the field must produce a greppable
  log line, never a silent death. Tags are `MT-*` (MT-Service, MT-Location, MT-OCR, MT-Export,
  MT-Repository, MT-ActivityRecognition).
- **The verification pass was the high-value move of the session.** It confirmed the scaffold was
  real (tests pass, T-008 columns present, blueprint-conformant) but caught two foundation cracks:
  the foreground service hand-rolled its own trip logic and **never called the unit-tested
  `TripLifecycleStateMachine`** (so the tested rulebook was dead code and an untested copy was what
  would actually run on testers' phones), and a missing `ServiceModule`. We explained the state
  machine to the user in plain terms ("the rulebook for a trip's life") — they're non-technical enough
  to want that, and responded well to it. Both were fixed by wiring the service *to* the tested API
  rather than changing tested code.
- **The Haiku coder question got a real, evidence-based answer.** The user asked whether the Haiku
  coders are the right choice / how they're performing / whether to delete+recreate them. Honest
  finding: **they had never been used — not once** (all prior work was specialist design/judgment).
  So instead of speculating, we used the T-018 mechanical grind as the first real test:
  android-engineer designed + did the judgment parts, **android-coder (Haiku) did the grind**,
  android-engineer reviewed. Verdict: **keep them.** The coder replicated a 5-part reference pattern
  across 4 files with no drift and got the tricky bits right; its one defect (main-thread blocking
  I/O) was *inherited from the reference implementation it was told to copy* (the pre-existing
  `CsvFileWriter`/`ExportViewModel`), not a coder failure — and the design→implement→review gate
  caught it. That bug became **T-019** and was fixed (two `withContext(Dispatchers.IO)` wraps). The
  lesson the user should remember: the tiered model works *because* of the review gate, not despite it.
- **The commit/push was a fast, multi-correction sequence — worth remembering the user's working
  style here.** They created the remote themselves mid-session, corrected the repo URL twice
  (`milage_sars_app` → **`SARS_compliant_milage_app`**), told us to update `.gitignore` before
  pushing, and when GitHub's GH007 blocked the push for exposing their real gmail, said "commit with
  an alias." We set the **repo-local** `user.email` to `willie84dutoit@users.noreply.github.com`
  (global config left untouched), amended/re-authored, folded the `.gitignore` hardening into the
  same commit, and pushed successfully. They move fast and expect inline correction, not a stall.
- **The email-in-history decision is the one thing a future session might be tempted to "fix" — DON'T,
  unless asked.** The 6 commits from earlier sessions still carry `<redacted-personal-email>` (the real gmail) and are
  now in the **public** repo's history (GH007 only enforced on the tip, so the amend let the old ones
  through). We offered a history-rewrite + force-push to scrub it; the user **explicitly chose to
  leave it as-is** (the gmail is guessable from the `willie84dutoit` username anyway). Future commits
  are clean via the repo-local alias. Do not re-raise this or rewrite history on your own initiative.
- **"Remember we are using docker" — interpreted and noted.** This was a reminder of the project's
  containerised backend convention. For this session it had no direct action: the Android commit
  doesn't touch the backend Docker/CI. But it surfaced a genuine gap worth flagging (below): the
  existing GitHub Actions CI is **backend-only** — pushing the Android app neither breaks it nor
  builds/tests the Android app.

### Open threads to pick up
- **Android is not in CI.** `.github/workflows/ci.yml` lints/tests the Python backend and builds its
  Docker image; it does nothing for the Android app. Pushing the app didn't break CI, but there's no
  automated `./gradlew test`/`assembleDebug` on push. Decide next session: add an Android CI job, or
  consciously defer it (given the goal is a locally-built sideloaded APK, deferring is defensible).
- **The deferred `MT-ActivityRecognition` log line** (US-009's last, unchecked acceptance criterion)
  rides with **T-002** — the registration `Task` doesn't exist yet to attach a failure listener to.
  Fold it in when T-002 builds the real ActivityRecognition registration.
- **Manager model:** the part-2/3 cost recommendation was to run the Manager on Sonnet day-to-day and
  reserve Opus for big debate/architecture sessions. This session ran on **Opus** — arguably justified
  by the verification/privacy/git judgment work, but worth a conscious choice next session rather than
  drifting onto the expensive model by default.

### The agreed next request / next step
The user explicitly said **"we can start t002 in a new session."** So this session ends here, clean.
The next session's job is **T-002 — automatic vehicle detection** (ActivityRecognition IN_VEHICLE, the
70%-confidence acquisition window via a short parallel `requestActivityUpdates()` subscription, 30s
silent retry, foreground-service lifecycle), following `team/blueprints/FULL_IMPLEMENTATION_PLAN.md`'s
`T-002.x` steps, and finishing the deferred `MT-ActivityRecognition` logging while there. T-001 is now
fully Done (verified → fixed → committed → pushed). Suggested opening request:
> "Start T-002 — vehicle detection, per the FULL_IMPLEMENTATION_PLAN steps. TDD where it makes sense,
> and wire in the deferred MT-ActivityRecognition logging."

### Practical environment notes for next session
- **Repo is now live and PUBLIC:** `https://github.com/willie84dutoit/SARS_compliant_milage_app.git`,
  local `main` tracks `origin/main`. Foundation commit is `c846b05`.
- **Git identity for this repo:** repo-local `user.email = willie84dutoit@users.noreply.github.com`
  (alias), `user.name = willie84dutoit`. Global config is unchanged (still the gmail) — so this alias
  applies *only* in this repo. Keep committing with the alias; GH007 will reject any commit that uses
  the gmail.
- **Commits need explicit verbal go-ahead every time** (unchanged standing rule) — a permission-dialog
  approval is not authorization. The user gave it for this session's push.
- Android: `ANDROID_HOME = C:\Android\Sdk`; emulator `test_device` (API 34) boots; `adb emu geo fix`
  works for GPS route injection (relevant to T-002 testing without driving).
- `./gradlew test` runs green (JBR JDK 21). Build toolchain is confirmed working — no build-loop risk.
- gcloud config / GCP project unchanged; billing still not linked (no backend work this session).
