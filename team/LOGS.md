# Team Logs — Automated Mileage Tracker

> **Append-only.** Never edit or delete past entries (per global no-delete rule).
> To correct something, add a new entry that supersedes the old one and reference it.
> The Manager writes here after every meaningful action: decisions, debates, delegations,
> cost rulings, blockers, and completed work.
>
> <!-- REDACTED 2026-06-19: personal Gmail address and the real GCP project id / gcloud config
> name were replaced below with `<redacted-personal-email>` / `<redacted-gcp-project-id>` /
> `<redacted-gcloud-config>` before this repo went public (open-source portfolio decision +
> pre-push leak-check rule in .claude/CLAUDE.md). The real values are not reproduced here. -->

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
- **Action / Decision:** Created GCP project **`<redacted-gcp-project-id>`** (display "Mileage Tracker"); created and activated isolated gcloud configuration **`<redacted-gcloud-config>`** (account `<redacted-personal-email>`, project `<redacted-gcp-project-id>`). Note: underscores invalid in project IDs / config names, so the config name uses a hyphen.
- **Rationale:** Isolates this app's cloud context from the user's other projects; prevents wrong-project deploys.
- **Cost impact:** $0 — project creation is free; **billing intentionally NOT linked** (`billingEnabled:false`). Linking is gated by `cost-architect` and should follow T-010 (cost model). Do not reuse another app's billing account.
- **Follow-up:** Before any deploy: run T-010 cost model → choose/confirm billing account → `gcloud billing projects link <redacted-gcp-project-id> --billing-account=<ID>`. To switch gcloud back to other work: `gcloud config configurations activate <name>`.

### [2026-06-18 15:30] DECISION — Session discipline rules + handoff redefined
- **Actor:** manager (user request)
- **Context:** User asked for a standing rule to always keep TASKS updated and always write the handoff at session end, and clarified the handoff is a *discussion carry-over*, not a status tracker.
- **Action / Decision:** Added "Session discipline" rules to `.claude/CLAUDE.md`; redefined `SESSION_HANDOFF.md` header + `/handoff` skill to capture conversation/intent (not status). Wrote the Session 2 handoff for today.
- **Rationale:** Status drifts if not updated live; the real loss between sessions is *context and intent*, which TASKS/LOGS don't capture.
- **Cost impact:** n/a
- **Follow-up:** Apply every session going forward.

### [2026-06-18 16:05] DONE — T-001 Android architecture blueprint (Option A, design only)
- **Actor:** manager (acting as android-engineer per project convention)
- **Context:** Option A from the session-2 handoff — turn the paper specs into an execution-ready
  design before any scaffold code is written, to keep T-001 build-loop-free in this session.
- **Action / Decision:** Wrote `team/blueprints/T-001-android-architecture-blueprint.md` covering:
  (1) single-Gradle-module decision over 4 separate modules, with justification and an explicit
  flip condition (KMP/second Android surface); (2) `TripEntity`/`TripPhotoEntity` Room schema incl.
  a decision on the `endTimestamp` sentinel value while a trip is `active`, and the exact
  no-duplicate-trip restart-recovery mechanism (single insert point, idempotent registrar,
  `getInProgressTrip()` checked before every `onStartCommand`); (3) the Hilt module/binding table
  (DatabaseModule, RepositoryModule, OcrModule, LocationModule, SettingsModule, ServiceModule);
  (4) the full trip lifecycle state machine — transient pre-trip states (SilentRetry/PromptPending)
  kept separate from the 4 locked `Trip.status` values, plus a resolved ambiguity: `pending_ocr` is
  a UI-blocking-wait state between stop-event and OCR/manual-value confirmation, not a data
  -completeness gate, so OCR failure never blocks completion as the brief requires; (5) the 7
  screens × ViewModel × repository-call table, enforcing "no DAO ever injected into a ViewModel";
  (6) a 9-step build order and an explicit android-coder-vs-android-engineer delegation split.
- **Rationale:** Makes scaffolding mechanical for a later session — no structural re-deciding needed
  — while keeping this session's token/build risk at zero (no Kotlin/Gradle files touched).
- **Cost impact:** n/a — confirmed zero cloud/network surface in this design, no cost-architect
  ruling needed for T-001 itself.
- **Follow-up:** T-001 moved to "blueprint done, scaffold not started" in TASKS.md. 4 open items
  flagged in the blueprint's closing section for `geo-sensors-specialist` (ActivityRecognition
  confidence wiring), `ml-ocr-specialist` (ML Kit client config), `security-crypto-specialist`
  (T-008 schema impact if a signature column is added later), and a confirmed no-op for
  `cost-architect` (nothing cloud-touching in this document).

### [2026-06-18 16:20] COST — T-010 backend cost model ruling (Option A, part 2)
- **Actor:** cost-architect (via manager delegation)
- **Context:** Option A's second piece — produce the per-user/month cost projection that gates
  linking billing to GCP project `<redacted-gcp-project-id>` (created 15:10 today, billing
  intentionally unlinked). cost-architect has no Write tool, so the Manager persisted its report.
- **Action / Decision:** **Ruling: APPROVE-WITH-CHANGES.** Full report written to
  `team/blueprints/T-010-backend-cost-model.md`. Projections: ~$0/mo at 20 users, ~$2-4/mo at
  1,000 users, ~$50-95/mo at 10,000 users (~$0.005-0.01/user/mo month-12 steady state). On-device
  ML Kit OCR confirmed at $0/mo; modeled Cloud Vision as a rejected alternative (~$2,700/mo at
  10k users — confirms the standing on-device decision). Identified the binding free-tier
  constraint: Firestore's 20,000-writes/day allowance, hit at ~3,300 users if syncing per-trip,
  ~20,000 users if batched per-user/day.
- **Rationale:** Five required changes attached to the APPROVE, all cheap to build in from day
  one and expensive to retrofit: (1) batch Firestore writes per-user/day, not per-trip; (2) photo
  upload to GCP Storage OFF by default (matches the local-first `file://` design already in the
  spec), compressed ≤300KB, with lifecycle rules; (3) Cloud Run `min-instances=0` with a small
  `max-instances` cap; (4) OCR stays on-device, Cloud Vision API stays disabled on the project;
  (5) the billing-link change must ship with a budget + 50/90/100% alerts **and** a
  Pub/Sub→Cloud-Function kill-switch, not alerts alone — alerts don't stop spend, only a
  kill-switch does.
- **Cost impact:** projected $0 at MVP/closed-testing scale (20 users); all projections rest on
  session-1 assumptions (6 trips/user/day, 15% photo-upload opt-in, 100% active-user ratio) —
  flagged as estimates, not measured data, and explicitly meant to be re-run once real telemetry
  exists.
- **Follow-up:** T-010 moved to Done in TASKS.md. Billing-link on `<redacted-gcp-project-id>` may
  proceed once safeguards 1-3 above are implemented alongside it — do not link "bare." No further
  action needed on T-010 itself unless usage assumptions need revisiting later.

### [2026-06-18 16:35] DEBATE — T-008 opener: trip-signing scheme for the tamper-evident SARS logbook
- **Actor:** manager
- **Context:** Option A's third (budget-permitting) piece. T-009 backend sync API is blocked on
  this decision; the T-001 blueprint deferred it and flagged that resolving it adds a column +
  migration to the now-locked `TripEntity` schema.
- **Action / Decision:** Framing the question for debate. Options: **(A)** per-trip digital
  signature — each completed Trip independently signed with a device-held Android Keystore key;
  **(B)** hash-chain — each trip's record embeds a hash of the previous trip's canonical data,
  forming a tamper-evident chain; **(C)** hybrid (chain + signature). Must decide the exact
  canonical fields covered (enough of `TripEntity` that tampering with classification, odometer
  values, business reason, or timestamps is detectable) and the Keystore key lifecycle (generation,
  non-exportability, behavior on reinstall/device change/key loss). Round 1: `security-crypto-specialist`
  (full scheme analysis, both options + canonical fields + Keystore lifecycle, recommend one) and
  `android-engineer` (schema/migration/Room-performance angle against the locked T-001 blueprint)
  spawned in parallel. Cost ruling from `cost-architect` to follow before the Manager decides.
- **Rationale:** Genuine trade-off (security guarantee strength vs. implementation/performance
  complexity vs. migration cost) — not an obvious default, so it goes through the debate process
  per `team/README.md`.
- **Cost impact:** TBD — expected near-zero (on-device crypto), `cost-architect` to confirm and
  flag any Phase-2 backend-verification implication.
- **Follow-up:** T-008. Decision + ruling to be logged once Round 1 returns.

### [2026-06-18 17:10] DECISION — T-008 resolved: per-trip signature + rolling tail-hash chain
- **Actor:** manager, synthesizing 2 debate rounds (`security-crypto-specialist` +
  `android-engineer`) and a confirmatory ruling from `cost-architect`.
- **Context:** Round 1 positions diverged: security-crypto-specialist argued Option C
  (signature + a **per-row** `previousTripHash` column on `TripEntity`, requiring a query for
  "the most recently completed trip" before each write); android-engineer argued pure Option A
  (signature only, no chain), rejecting the per-row column on three grounds — it turns a clean
  single-row `UPDATE` into a read-then-write transaction; "previous trip" is ambiguous because
  trips don't finalize in start-time order (`pending_business_reason` trips can finalize hours
  after later-started trips complete); and a crash mid-write leaves an undetectable chain gap the
  existing recovery logic (designed for "no duplicate trips," not "no chain gaps") can't repair.
  android-engineer offered a narrower fallback: a single rolling tail-hash in a one-row DataStore
  settings entry instead of a per-row column. Round 2 tested that fallback against both objections.
- **Action / Decision:** **Adopted android-engineer's rolling-tail-hash design, with
  security-crypto-specialist's cryptographic mechanism on top.** Final scheme:
  - **Per-trip ECDSA P-256 signature**, Android Keystore (StrongBox attempted, TEE fallback,
    outcome recorded not swallowed), computed at the literal terminal transition into `completed`
    (one call site, mirroring the §2 no-duplicate-trip insert-point discipline).
  - **Chain mechanism:** a single `chainTailHash` value in a one-row DataStore settings entry
    (already in the Hilt graph via `SettingsModule`), folded into each trip's signed payload as
    `prevTail`, advancing in **finalization order** (not start order — chain-order ≠
    calendar-order by design; calendar truth lives in the independently-signed
    `startTimestamp`/`endTimestamp`). Verified (Round 2, security-crypto-specialist): deleting a
    historical trip still breaks the chain at the same point a per-row column would have caught
    it — the detection property doesn't depend on where the link is stored.
  - **Write-order / durability (Manager synthesis — R2 left this split):**
    security-crypto-specialist proposed Room-write-then-DataStore-write;
    android-engineer proposed DataStore-write-then-Room-write, each citing a different
    self-heal direction. Resolved by making **Room the sole durability anchor** and DataStore's
    tail a **derived, rebuildable cache**: write the signed trip row to Room first; best-effort
    update the DataStore tail second; on every cold start (existing recovery hook per §2),
    unconditionally recompute the tail from the most-recently-signed Room trip and overwrite
    DataStore. A crash in either order self-heals from Room alone — no cross-store transaction
    needed, and this was reachable directly from each specialist's own Round 2 reasoning.
  - **Schema impact (exact): two new nullable `TripEntity` columns** — `signatureBase64: String?`,
    `signingKeyId: String?` (a local Keystore alias, generated lazily at first trip completion,
    zero backend dependency — resolves android-engineer's Round 2 timing question). **No
    `previousTripHash` column** (withdrawn by security-crypto-specialist in Round 2). One additive
    `Migration(1, 2)`.
  - **Canonical signed fields (fixed order):** id, classification, startTimestamp, endTimestamp,
    startOdometerKm, endOdometerKm, verifiedOdometerKm, distanceKm, businessReason, status,
    prevTail, tripSequenceNumber. Excluded: createdAt/updatedAt (mutate on benign touches),
    lat/long (privacy — distanceKm already carries the claim; keeps precise location out of the
    integrity proof). Fixed-precision serialization (2dp decimals as strings, epoch millis,
    lowercase enum strings, explicit JSON `null`) — full spec in security-crypto-specialist's
    Round 1 transcript, to be handed verbatim to whoever implements T-006.
  - **Known, documented limitation:** tail-truncation (delete the *last* trip(s) so the tail
    naturally reflects a shorter, internally-consistent history) is not detectable locally by
    either design — closed at the backend in Phase-2 via a monotonic `tripSequenceNumber` exposing
    gaps server-side. Not a blocker now: no backend/auditor exists yet to exploit it.
- **Rationale:** The per-row column and the rolling-tail design give the *same* tamper-evidence
  guarantee (deletion/reordering detection) — the disagreement was entirely about where the link
  lives, not whether chaining is needed. The rolling-tail design wins because it preserves the
  blueprint's single-row DAO shape (no new read-then-write transaction, no query-ordering
  ambiguity against a state machine where finalization order ≠ start order) and reuses a store
  already in the Hilt graph. Pure signature-only (no chain at all) was rejected because deleting
  private trips to inflate the business-use ratio is the single most likely tamper for this
  domain, and a bare per-trip signature is blind to it.
- **Cost impact:** **APPROVE** (cost-architect). MVP-phase signing is 100% on-device
  (Keystore + Room + DataStore) — confirmed zero GCP cost, nothing missed. Phase-2 addition
  (a `device_keys/{uid}/{keyId}` Firestore registry + per-trip signature/chain verify compute on
  sync) is noise inside T-010's existing projections (~$0 @20, ~$2-4/mo @1k, ~$50-95/mo @10k) and
  does not move the binding Firestore 20k-writes/day constraint T-010 already identified. Three
  conditions carried forward into the T-009 spec (not blocking T-008): fetch the device public key
  once per sync batch, not per trip; the key registry goes through the same partitioned security
  rules + App Check as trip writes; re-cost the key-registration event rate from real telemetry
  once available.
- **Follow-up:** T-008 → Done (decision logged; implementation rides with T-006). T-001 blueprint's
  open-questions §3 updated to point here instead of leaving the schema impact open. **Note for
  whoever executes T-006:** build the two signing columns + migration in from the start per this
  decision — don't build the bare schema first and retrofit.

### [2026-06-19 09:30] DECISION — Project going open source (portfolio); non-commercial license; pre-push leak scan; explicit push go-ahead rule
- **Actor:** manager (user request)
- **Context:** User decided this repo will be public, part of their showcase portfolio, but must
  not be usable for any commercial/monetary purpose. User also asked for a standing rule that
  Claude never commits/pushes on tool-permission approval alone, and for a pre-push leak check
  (emails, secret keys, service-account files) before the first public push.
- **Action / Decision:**
  1. Added `LICENSE` (PolyForm Noncommercial 1.0.0) — free for personal/private/educational use,
     commercial/monetary use prohibited. Attributed to the GitHub alias `willie84dutoit`, not a
     real name/email (per the new leak-check rule below).
  2. Added two rules to `.claude/CLAUDE.md` under "Project rules that override defaults":
     a mandatory pre-push leak check (emails, API keys/tokens, `serviceAccount*.json` / keystores /
     `google-services.json`), and a mandatory explicit-verbal-go-ahead requirement before any
     `git commit` or `git push` — a tool-permission-dialog approval does **not** count as that
     go-ahead; the Manager must state what it's about to commit/push and wait for the user to say
     so in words, every time.
  3. Ran the leak scan against the full tracked file set (`git ls-files`): found one real leak
     (personal Gmail address, literal text in `team/LOGS.md` and `team/SESSION_HANDOFF.md`) and,
     per user's explicit choice, also redacted the real GCP project id and gcloud config name
     (not a credential by itself, but unnecessary infra detail for a public portfolio repo).
     Redacted across `team/LOGS.md`, `team/SESSION_HANDOFF.md`, `team/TASKS.md`, and
     `team/blueprints/T-010-backend-cost-model.md` with placeholder tokens (`<redacted-personal-email>`,
     `<redacted-gcp-project-id>`, `<redacted-gcloud-config>`) and an HTML-comment note explaining
     the redaction (no-delete rule honored via documented substitution, not by repeating the
     original secret in the comment). No credential files (`.env`, `.json` keys, keystores,
     `local.properties`) were ever tracked — `git ls-files` confirms zero such files in history.
  4. Confirmed no API keys, OAuth tokens, or private-key blocks exist anywhere in tracked files.
- **Rationale:** Public + portfolio changes the risk profile from "private, trusted collaborators"
  to "anyone, indexed forever" — redaction has to happen before the *first* push, not after.
- **Cost impact:** n/a (docs/license only).
- **Follow-up:** None outstanding from this decision. GitHub repo creation (public, no license
  picker — `LICENSE` file already supplies PolyForm Noncommercial) is still pending the user
  creating it manually on github.com and sharing the remote URL.

### [2026-06-19 10:15] NOTE — Board reconciliation: T-001 Android scaffold found built on disk, never logged
- **Actor:** manager (found via `/standup`, before delegating any new work)
- **Context:** `team/TASKS.md` still read "scaffold code not started" for T-001, but `git status`
  showed a full, untracked `app/` Gradle project plus root `build.gradle.kts`/`settings.gradle.kts`/
  `gradlew` (file timestamps 2026-06-19 08:06–08:31, i.e. earlier today). The board had silently
  drifted from reality — exactly the failure mode the session-discipline rule exists to prevent.
- **Action:** Asked the user directly rather than guessing how it got there. User confirmed they
  will commit it themselves and reaffirmed the standing rule: the Manager never commits/pushes
  without an explicit verbal go-ahead, every time, regardless of how a tool-permission prompt was
  answered. Manager then updated T-001 in `team/TASKS.md` to match what's actually on disk: full
  package tree per the blueprint (domain/data/service/ui/DI), 4 domain unit tests, and a **successful
  debug build** (`app/build/outputs/apk/debug/app-debug.apk` exists — Hilt/KSP codegen + resource
  merge completed). Flagged as still open: nothing is committed; no code review against the
  blueprint or the T-008 signing-column requirement has happened; `./gradlew test` has not actually
  been *run* (only confirmed to compile/exist).
- **Rationale:** Recording state truthfully before planning today's work, per the explicit
  CLAUDE.md instruction: "the board must never silently drift from reality."
- **Cost impact:** n/a (local build only, no cloud touch).
- **Follow-up:** Candidate next task — android-engineer review pass of the existing scaffold against
  the blueprint + T-008, then `./gradlew test`, before T-001 is marked Done. User to commit when ready.

### [2026-06-19 10:30] NOTE — Full user-story + kanban-board pass: closed the T-012/T-013 story gap, added T-016
- **Actor:** manager (user request: "I want all the user stories to be written... and a full kanban
  board" — content owned by the Manager per CLAUDE.md, not delegated)
- **Context:** Auditing `team/TASKS.md` against `userstories/` showed every task had a user story
  except T-012 (store publishing readiness) and T-013 (iOS port) — no epic covered Phase-3/4. Writing
  T-012's stories also surfaced a real gap in TASKS.md itself: the Huawei HMS technical swap (Account
  Kit / Location Kit incl. `ActivityIdentificationService` / ML Kit Text Recognition / backend-routed
  sync, since direct Firestore needs GMS) had no owning task — `compliance-qa-specialist` only owns
  publishing *readiness*, not the engineering swap.
- **Action:**
  1. Added **T-016** to `team/TASKS.md` (Huawei HMS technical adaptation; owner geo-sensors-specialist
     + ml-ocr-specialist + backend-engineer; Phase-4; blocked by T-002, T-005, T-009).
  2. Added **US-107** (account deletion + data removal) to `userstories/epic-02-cloud-sync-compliance.md`
     — store-mandated requirement (Play + App Store both require in-app deletion) that had no story.
  3. Created **`userstories/epic-03-platforms-publishing.md`** with 4 new stories: **US-201** (iOS
     mirrors Android MVP, T-013), **US-202** (pass Google Play + Apple App Store review, T-012),
     **US-203** (Huawei HMS technical compatibility, T-016), **US-204** (pass Huawei AppGallery
     verification, T-012). Acceptance criteria drawn directly from `publisheing guide.md` §6–7
     (prominent disclosure, Info.plist justifications, Sign in with Apple mandate, 20-tester rule,
     Huawei developer ID verification, PIPL/ICP called out as a separate undertaking, not assumed).
  4. Updated `userstories/README.md` epic list and `agile/board.md` Backlog to include all 5 new
     stories (US-107, US-201–US-204), plus bumped the board's "Last moved" date.
- **Rationale:** User asked for complete story + board coverage; closing a story gap that also
  surfaces a real task-ownership gap (T-016) is exactly the kind of drift this session's board
  reconciliation (see prior NOTE) is meant to catch before it's forgotten.
- **Cost impact:** n/a (planning docs only).
- **Follow-up:** T-016 has no specialist work started — it's Phase-4 and blocked by T-002/T-005/T-009,
  none of which are done yet, so it's correctly Backlog, not Ready. No other action needed now.

### [2026-06-19 10:45] NOTE — Kanban board rebuilt to task-and-feature granularity (user: "every single task")
- **Actor:** manager (user request: the story-level board was "insufficient" — wanted "every single
  task" represented, not just one line per US-### story)
- **Context:** The previous `agile/board.md` had one line per `US-###` story (18 lines) and silently
  skipped 3 tasks entirely (T-001, T-008, T-010 had no card because they're not user-facing). That's
  not what was asked for.
- **Action:** Rewrote `agile/board.md` from scratch: now **every `T-001`–`T-016` task gets its own
  card** (16 cards total, none skipped), and each card lists **every discrete feature/sub-item**
  inside that task as a checkbox — pulled from `team/TASKS.md`'s own description plus the linked
  `US-###` acceptance criteria, so nothing already written elsewhere got re-invented, just surfaced
  at the board level. Checkboxes reflect actual state per the T-001 reconciliation and existing
  TASKS.md status (e.g. T-001's scaffold/build boxes are checked, review/test/commit boxes are not;
  T-008/T-010 are fully checked and sit in Done; T-014/T-015 show exactly which sub-items are done
  vs. open). WIP-3 limit on In Progress preserved (T-001, T-014, T-015 — exactly 3).
- **Rationale:** A kanban board that hides 3 of 16 tasks and collapses every task's internal feature
  list into a single line doesn't give an accurate "what's actually left" view — the user explicitly
  wants to see every feature, not just every story.
- **Cost impact:** n/a (planning doc only).
- **Follow-up:** Going forward, when a task's sub-feature is finished, check its box on the board in
  the same turn the work is recorded in `TASKS.md` — don't let the two drift apart again.

### [2026-06-19 11:30] NOTE — FULL_IMPLEMENTATION_PLAN.md rewritten as a literal, decision-free build guide
- **Actor:** manager (user request, escalated twice: first "every single task," then "any junior
  developer will be able to understand... they should not have to think of anything")
- **Context:** A prose "how it will be done" per task (first draft of this file) still left 4 real
  technical questions flagged **OPEN** (T-002 confidence sourcing, T-004 GPS noise floor, T-005 OCR
  confidence derivation, T-011 analytics taxonomy) and was written at architecture-summary level,
  not literal step-by-step. User explicitly rejected further agent delegation mid-session ("forget
  about agents and shit") and asked for numbered micro-steps (`T-001.1`, `T-001.2`, ...) each with a
  literal **How**, plus TDD built into every code-producing step.
- **Action:** Before the "no more agents" instruction landed, two specialist rulings were already
  obtained and used as the technical basis for the rewrite (not re-litigated, just applied):
  geo-sensors-specialist closed T-002 (confidence-acquisition window: a short-lived parallel
  `requestActivityUpdates()` subscription alongside the always-on Transition API trigger, max-so-far
  confidence within the 30s window) and T-004 (8.0m GPS noise floor; 15s time-batched Room distance
  writes, not per-update or per-N-updates); ml-ocr-specialist closed T-005 (ML Kit's on-device API
  has no confidence field, so a composite 4-signal weighted score — digit-count, isolation,
  vertical-position, structural-purity — stands in for it, gated at 80). T-011's analytics taxonomy
  was decided directly by the Manager per the explicit "no more agents" instruction (6 events:
  trip_started/classified/completed/flagged/approved, export_generated). `FULL_IMPLEMENTATION_PLAN.md`
  was then rewritten end-to-end: every T-001–T-016 task broken into numbered sub-steps, each with
  literal code/commands/config (pinned Gradle dependency versions, exact Kotlin/Python snippets,
  exact `gcloud`/`gh`/Room-query text) and, for every code-producing step, an explicit
  write-test-RED-implement-GREEN sequence. A later user correction ("not to yourself, to anybody")
  prompted stripping internal meta-framing ("written by the Manager," "ruling from this session")
  so the document reads as a standalone spec for any developer, not Manager process notes.
- **Rationale:** the user's bar is explicit: a junior developer should be able to follow this
  document and build the app by Googling specific API names, without inventing or deciding
  anything. Open technical questions and architecture-level prose both fail that bar; literal
  numbered steps with decided constants don't.
- **Cost impact:** n/a (planning doc only; the two specialist agent calls used for T-002/T-004/T-005
  were Sonnet/Haiku-tier reads with no cloud-cost implication).
- **Follow-up:** `team/TASKS.md` and `agile/board.md` still describe these tasks at checkbox-feature
  granularity, not micro-step granularity — they point to this plan for the "how," not duplicate it.
  No other action needed unless the user wants the board itself restructured to match.

### [2026-06-19 11:50] NOTE — FULL_IMPLEMENTATION_PLAN.md corrected: specify, don't implement; add Verify to every step
- **Actor:** manager (user correction: "i never said generate code... i said specify the tasks,"
  followed by "and how to accomplish...")
- **Context:** The previous rewrite of this plan embedded full Kotlin/Python class implementations
  per micro-step — the user's actual ask was a specification (which API/class/method/config/value
  to use) for a developer to implement against, not finished source code to paste in. The follow-up
  message asked for an explicit "how to accomplish" check on top of that.
- **Action:** Rewrote every micro-step in `FULL_IMPLEMENTATION_PLAN.md` to drop embedded
  implementation code, keeping only: the exact API/class/method names, exact config/dependency
  values, exact thresholds/constants already decided this session (8m GPS noise floor, 80-point
  OCR composite gate, 15s distance-flush interval, the 4-signal OCR scoring weights, the 6-event
  analytics taxonomy), and short literal commands (gradle/adb/pip) where the action genuinely is a
  command. Added a **Verify** line to every single micro-step across all 16 tasks — a concrete,
  checkable statement of how to confirm that specific step actually worked (a test assertion, a
  manual device check, a file/schema inspection) — not just a "How" describing the approach.
- **Rationale:** the user is the one writing the code; the plan's job is to remove ambiguity about
  *what* to build against and *how to know* each piece is done, not to pre-write the implementation
  for them.
- **Cost impact:** n/a (planning doc only).
- **Follow-up:** none — this is the corrected, current version of the plan.

### [2026-06-19 12:05] NOTE — Added T-017 (Compose UI screens): the plan had no front-end task
- **Actor:** manager (user flagged: "i dont see any front end tasks")
- **Context:** T-002–T-007 each mention a screen in passing while specifying ViewModel/domain logic
  (e.g. T-005.5 "show a numeric text field"), but no task owned the actual UI build — layout,
  Material3 components, navigation graph, theme, or the loading/empty/error/limited-mode visual
  states for any of the 7 screens in `developer_handoff_brief.md` §7. This was a genuine gap, not a
  restated existing task.
- **Action:** Added **T-017 · Compose UI screens — layout, navigation, visual design** to
  `team/TASKS.md` (owner android-engineer + a11y-architect, Phase MVP, blocked by T-001) and to
  `agile/board.md` (full card, 11 checkboxes: nav graph + theme, one box per screen, loading/empty/
  error states, accessibility pass). Added a matching `T-017.1`–`T-017.8` section to
  `FULL_IMPLEMENTATION_PLAN.md` in the same Task/How/Verify format as every other task, explicitly
  scoped as owning *layout and navigation* while T-002–T-007 keep owning *state and logic* for the
  same screens — the two are meant to be built alongside each other, not sequentially.
- **Rationale:** a complete build plan that never specifies what the screens actually look like or
  how navigation connects them is missing a whole layer of real work — exactly the kind of gap this
  session has been closing one user-flagged miss at a time.
- **Cost impact:** n/a (planning doc only).
- **Follow-up:** none.

### [2026-06-22 09:00] DECISION — Scope/parking question resolved: keep everything active; T-008 stays in MVP
- **Actor:** manager (user choice, answering the two open forks carried over from the part-4 handoff)
- **Context:** The part-4 handoff (2026-06-19) left two real questions unresolved and explicitly said
  to ask before re-attempting: (1) given the user's stated real goal is a personal sideloaded `.apk`,
  should T-009 (backend sync), T-011 (analytics/SARS), T-012 (store publishing), T-013 (iOS), and
  T-016 (Huawei HMS) be Parked? and (2) does T-008 (crypto signing, for SARS audit defensibility)
  still belong in the MVP for purely personal use? The half-started "Parked" annotation from part-4
  was never completed, so the board still showed all tasks at full priority.
- **Action / Decision:** User chose **keep all tasks active** (do NOT park T-009/T-011/T-012/T-013/T-016)
  and **keep T-008 in the MVP build**. No board edits required — `team/TASKS.md` and `agile/board.md`
  already reflect this (nothing was ever actually parked). The part-4 "should I park these" thread is
  now closed.
- **Rationale:** User's decision — the broader multi-platform/published roadmap stays on the board
  even though the immediate build target is a personal APK; T-008 kept as cheap insurance ($0 cost,
  already designed, folds into T-006's Room schema) in case the mileage is ever claimed on tax.
- **Cost impact:** n/a (no scope removed; no new spend authorized).
- **Follow-up:** Planning phase is closed. Next actual work = build, starting with an android-engineer
  review pass of the existing on-disk scaffold against the T-001 blueprint + the T-008 signing columns
  (`signatureBase64`/`signingKeyId` on `TripEntity`), then `./gradlew test`, before T-001 → Done.

### [2026-06-22 09:30] DONE/NOTE — T-001 verification pass complete; 2 findings block "Done"; T-018 (field logging) born
- **Actor:** android-engineer (review only — no commits, no edits) via manager delegation
- **Context:** User confirmed today's goal: get T-001 to a *verified* Done (not just "it compiled")
  because the app goes to human field testers and the MVP is due ~10 sessions out (by Friday). User
  also raised a new requirement: proper logging + try/catch so field-test failures produce logs the
  Manager can review, not silent deaths.
- **Action / Decision:** android-engineer audited the on-disk scaffold against
  `team/blueprints/T-001-android-architecture-blueprint.md`, confirmed the T-008 signing columns, and
  ran `./gradlew test`. Results:
  - ✅ `./gradlew test` = **BUILD SUCCESSFUL**, all 5 unit-test classes (Classification, OdometerParser,
    CsvExport, TripLifecycleStateMachine, + bonus HaversineDistance) pass for debug AND release variants.
  - ✅ T-008 columns `signatureBase64`/`signingKeyId` present in the v1 `TripEntity` schema (no
    Migration(1,2) needed — correctly built into v1, schema JSON checked in). 18-column entity matches
    blueprint §2 + the 2 signing columns. Nothing missing.
  - ✅ Package tree, 5 Hilt modules, 7 screens+ViewModels, DAOs, repositories, dependency-direction
    rule (no DAO/Entity leak into ui) all conform.
  - ⚠️ **HIGH BLOCKER:** `TripTrackingForegroundService` hand-rolls its own start/stop/timer logic and
    **never invokes `TripLifecycleStateMachine`** — the blueprint's designated "highest-risk file." The
    tested state machine is dead code at runtime. Spec/impl have diverged on the one file that most
    needs the trusted path. Needs a design decision: wire it through (recommended) or formally amend
    the blueprint.
  - ⚠️ **MEDIUM:** `service/di/ServiceModule.kt` (blueprint §3) was never created — the module that
    would wire the state machine in. Directly related to the HIGH finding.
  - ℹ️ WorkManager + hilt-work deps are declared but no `Worker` class exists — brief-vs-blueprint
    mismatch (blueprint's 9-step build order never lists a Worker as a T-001 deliverable). Tracked as
    follow-up, likely belongs with T-007 if CSV export should be backgroundable. Not a T-001 defect.
  - 🔴 **Observability gap (new work):** ZERO logging anywhere in the app (no Timber, no `android.util.Log`),
    ZERO try/catch at any I/O boundary (foreground service, ActivityRecognition, location callback, ML
    Kit OCR, CSV export, all Room writes). For a field tester who can't run logcat, every such failure
    looks identical to the app silently doing nothing. Also a latent correctness bug: an uncaught OCR
    exception could trap a trip in `pending_ocr` forever, violating the blueprint's "trip must always
    save even if OCR unavailable" rule.
- **Rationale:** Verifying before building on top is exactly what protects the 10-session timeline —
  better to fix a foundation crack now than discover it in session 8.
- **Cost impact:** n/a — review only; the proposed logging work is $0 (on-device file logging, no
  cloud sink, no network permission).
- **Follow-up:** (1) T-001 stays In Progress until the state-machine bypass + missing ServiceModule are
  resolved (decision to be logged) and the fix verified. (2) New task **T-018** — field-debuggability:
  Timber + custom file-logging Tree + "export debug log" action on Settings (reusing CsvFileWriter's
  MediaStore pattern) + try/catch at the 7 named crash-prone surfaces with greppable `MT-*` tags;
  android-engineer designs boundary placement + tag taxonomy, android-coder does the mechanical grind.
  Recommended to bundle T-018 with the T-001 fix since both edit the same files (service, OCR client,
  CSV writer, repositories). Full per-surface detail in the android-engineer review returned this session.

### [2026-06-22 11:00] DONE/NOTE — T-001 fixed to verified-Done (engineering); T-018 built; first real Haiku-coder run evaluated
- **Actor:** manager, orchestrating android-engineer (design + judgment) → android-coder (Haiku, grind) → android-engineer (review)
- **Context:** User go-ahead to bundle the T-001 blocker fix with the T-018 logging work (both touch the
  same files), and chose "wire the service through the tested state machine." User also asked whether
  the Haiku coders are the right choice / performing / worth keeping — this session produced the first
  actual android-coder run, so the evaluation is now evidence-based, not speculative.
- **Action / Decision:**
  - **T-001 fix (android-engineer):** created `service/di/ServiceModule.kt` (blueprint §3); refactored
    `TripTrackingForegroundService` so `TripLifecycleStateMachine` drives both the start-side
    transient-phase resolution and the stop-side `TripStatus` (replacing hand-rolled hardcoded
    `ACTIVE`/`PENDING_OCR`). Adapted the *service* to the already-tested state-machine API rather than
    changing tested code. Locked v1 thresholds still flow through unchanged. `./gradlew test` green.
    **T-001 engineering is complete** — only the user's git commit remains before the card moves to Done.
  - **T-018 judgment half (android-engineer):** added Timber 5.0.1 + `FileLoggingTree` (rotating
    on-device log file under filesDir, ~2MB→`.1`), planted in `MileageTrackerApplication`; fixed the
    real correctness bug (OCR exception now degrades to `NoTextFound`, can't trap a trip in
    `pending_ocr`); wrote 2 reference try/catch implementations (`MlKitOdometerOcrClient`,
    `CsvFileWriter`→`CsvWriteResult`).
  - **T-018 grind half (android-coder / Haiku):** `onDestroy` per-step `runCatching` isolation
    (`MT-Service`); `TripLocationCallback` try/catch leaving the GPS anchor untouched on failure
    (`MT-Location`); `TripRepositoryImpl` logs unknown-tripId before throwing, message text preserved
    (`MT-Repository`); new `DebugLogFileProvider` + Settings "Export debug log" button
    (`DebugLogExportResult`/`DebugLogExportUiResult`, `MT-Export`). `./gradlew test` green.
  - **Review (android-engineer):** logic, layering, error-handling, Hilt wiring all correct.
    Found **one HIGH bug: main-thread blocking disk I/O** inside `viewModelScope.launch` in BOTH the
    new debug-log export AND the pre-existing `CsvFileWriter`/`ExportViewModel` it was told to copy —
    i.e. an inherited flaw from the reference, not a coder mistake. Fix = two one-line
    `withContext(Dispatchers.IO)` wraps. ActivityRecognition logging deliberately deferred to T-002.
- **Coder evaluation (answers the user's question):** Haiku coder replicated a 5-part reference
  pattern across 4 files with no drift on naming/layering/error-rules, and got the tricky bits right
  (anchor-untouched-on-failure, per-step teardown isolation). Its one defect was inherited from the
  reference spec itself. **Verdict: keep the Haiku coder tier for well-specified "implement against a
  reference pattern" grind; the design→implement→review gate catches the cross-cutting issues Haiku
  can't be expected to.** No deletion/recreation of coder agents warranted.
- **Cost impact:** $0 — all on-device (Timber file log, no cloud sink, no network permission).
- **Follow-up:** (1) NEW small task **T-019** — wrap `DebugLogFileProvider.exportDebugLogToDownloads()`
  AND `CsvFileWriter.writeToDownloads()` calls in `withContext(Dispatchers.IO)` from their ViewModels
  (mechanical, android-coder-appropriate). (2) T-018's remaining `MT-ActivityRecognition` logging rides
  with T-002. (3) T-001 → Done once the user commits.

### [2026-06-19 12:20] NOTE — Board regenerated to match FULL_IMPLEMENTATION_PLAN.md's micro-step granularity
- **Actor:** manager (user request: "fix it according to the plan — do not forget to add the
  roleplayer-- or dont delete the roleplayer")
- **Context:** `agile/board.md`'s checklists were at feature-summary granularity (one line per
  capability), while `FULL_IMPLEMENTATION_PLAN.md` now defines literal `T-XXX.Y` micro-steps with
  How/Verify for the same tasks — the two were out of sync in granularity, even though both were
  individually accurate. "Roleplayer" is read as the **owner role** line on each card (which
  specialist is responsible) — explicitly preserved and relabeled `**Owner role:**` on every card,
  not dropped.
- **Action:** Rewrote every Backlog/In-Progress/Done card's checklist to use the plan's exact
  `T-XXX.Y` step titles instead of feature summaries, so a box on the board and a step in the plan
  are now the same unit of work, traceable 1:1. Kept T-014/T-015 (DevOps/QA) on their existing
  checklists since the plan doc doesn't cover those two tasks at micro-step level. T-008's design
  decision stays in Done (matching its TASKS.md status); its 5 implementation steps (T-008.1–8.5)
  are listed once, under T-006's card, since that's where TASKS.md says the signing implementation
  rides — not duplicated on both cards. T-001's card reflects that the scaffold build succeeded
  (steps 1.1–1.5 checked) while review/test/commit remain open, matching the earlier reconciliation
  note.
- **Rationale:** the board's job is to show "what's left," and that's only true if its checkboxes
  match the same granularity as the plan a developer is actually following — otherwise a checked
  box on the board doesn't tell you anything concrete was finished.
- **Cost impact:** n/a (planning doc only).
- **Follow-up:** none.
