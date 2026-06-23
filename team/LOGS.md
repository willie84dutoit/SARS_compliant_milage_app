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

### [2026-06-22 12:30] DONE/DECISION — Foundation committed + pushed to public GitHub; email-in-history decision
- **Actor:** manager (user gave explicit verbal go-ahead to "commit and push")
- **Context:** End-of-session push of the verified foundation (T-001 + T-018 + T-019) to a new public
  GitHub repo for the portfolio.
- **Action / Decision:**
  - Ran the mandatory pre-push leak scan: no credential files committable (only `local.properties`
    on disk, correctly git-ignored); no emails/API keys/private keys in any file content; no secrets
    in config. `.gitignore` strengthened with Android build-artifact coverage (`.kotlin/`, `*.apk`,
    `*.aab`, `*.ap_`, `*.dex`, `*.hprof`, `release/`) per user request before pushing.
  - Added requirement id **US-009** (field debuggability) to `userstories/epic-01-trip-capture.md`,
    linking T-018 + T-019 to a story per the project pattern.
  - Committed the full Android MVP scaffold + foundation hardening + planning docs + LICENSE
    (commit `c846b05`, 99 files). No Co-Authored-By line (global no-attribution setting honored).
  - **Remote:** user first gave `milage_sars_app.git`, then corrected to
    `github.com/willie84dutoit/SARS_compliant_milage_app.git` (no remote had been added yet, so no undo
    needed). Push initially **rejected by GitHub GH007** — the commit carried the real gmail. Fixed by
    setting repo-LOCAL `user.email` to the alias `willie84dutoit@users.noreply.github.com` (global
    config untouched) and re-authoring via `--amend --reset-author`. Push then succeeded; `main`
    tracks `origin/main`.
  - **Email-in-history decision (user choice):** the 6 pre-existing commits from earlier sessions
    (`d9116b6`…`498204c`) carry `<redacted-personal-email>` (the user's real gmail) as author+committer and are now in the
    PUBLIC repo history. GH007 only enforced on the tip commit, so the amend let them through. Offered
    a history-rewrite + force-push to scrub the gmail from all commits; **user chose to LEAVE IT AS-IS**
    (the gmail is already guessable from the `willie84dutoit` username). No history rewrite performed.
- **Rationale:** User's explicit decision; future commits are clean via the repo-local alias config,
  so no new exposure. The historical exposure is accepted PII per the user's own call.
- **Cost impact:** n/a (GitHub free public repo).
- **Follow-up:** None required. If the user ever changes their mind, the scrub is a `git filter-repo`
  email-map + force-push (safe while the repo has no other collaborators). T-001 now fully Done
  (committed). Next session: T-002.

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

### [2026-06-22 (close)] DONE — T-001 (Scaffold Android project) confirmed Done; board reconciled
- **Actor:** manager (delegated fix-verify to android-engineer)
- **Context:** User believed T-001 was already done; the `team/TASKS.md` In-Progress card still listed
  two open review blockers (HIGH: foreground service bypassing `TripLifecycleStateMachine`; MEDIUM:
  missing `service/di/ServiceModule.kt`). Manager dispatched android-engineer to fix both and re-verify.
- **Action / Decision:** android-engineer found **both blockers were already resolved in commit `c846b05`**
  (the T-018 logging pass — same files, exactly as the earlier "Next" plan predicted), not outstanding work.
  Verified by reading the actual sources (`TripTrackingForegroundService` injects + drives the state machine
  through `onStartEvent`/`resolvePromptPendingIntoActiveTrip` and `handleStopEvent`/`onStopEvent` for all 3
  stop paths, no hardcoded `TripStatus`; `ServiceModule.kt` present per blueprint §3) and a clean
  `./gradlew clean test` → BUILD SUCCESSFUL, 62 tasks, all 5 unit-test classes green on debug + release.
  No new source changes were required (working tree clean). T-001 declared **DONE**. Reconciled records:
  `agile/board.md` T-001 card moved In Progress → Done (WIP 3/3 → 2/3); `team/TASKS.md` card annotated DONE
  with blockers struck through + a Done-section pointer added.
- **Rationale:** The work was finished and committed; only the tracking records had drifted. Closing the
  card and fixing the drift keeps the board honest and unblocks the MVP backlog.
- **Cost impact:** n/a ($0 — no cloud/architecture change; on-device only).
- **Follow-up:** T-001 unblocks T-002–T-007 and T-017. One non-blocking carry-over: ActivityRecognition
  auto-start is registered but not yet feeding `TripStartEvent.ConfidentVehicleEntry` into the state machine
  — deferred to T-002 (TODO tagged in the service class doc).

### [2026-06-22 (close)] DELEGATION — T-002 detection design (geo-sensors-specialist)
- **Actor:** manager → geo-sensors-specialist
- **Context:** T-001 Done unblocked T-002. User chose to start T-002 next. Owners (geo-sensors +
  android-engineer) overlap on service/receiver/state-machine files, so ran sequentially (NOT parallel),
  design→implement split.
- **Action / Decision:** Delegated the detection DESIGN to geo-sensors-specialist (step 1 of 2). Spec
  written to `team/blueprints/T-002-vehicle-detection-spec.md`. Findings: T-002.2 event types already
  exist (only 2 tests missing); T-002.3 receiver/registrar shells exist with TODO bodies; T-002.4
  confidence-acquisition window is net-new — designed `ConfidenceAcquisitionWindow` (5s second
  subscription, running-MAX confidence, 30s TestScope-driveable timer, unregister-before-emit for battery
  safety). No production code changed (spec only). Open flags: none on core design; widening the 30s window
  later must go via /team-debate (battery cost); ENTER+EXIT both registered intentionally (reserved for T-004).
- **Rationale:** Plan constants are locked; the real judgment was the confidence-window/battery design,
  which is geo-sensors' domain. Implementation goes to android-engineer/android-coder next.
- **Cost impact:** n/a ($0 — on-device ActivityRecognition only, no cloud/architecture change).
- **Follow-up:** Step 2 — android-engineer (+ android-coder) implements from the spec & runs `./gradlew test`.
  T-002.5 Bluetooth deferred to end of T-002.

### [2026-06-22 (close)] DONE — T-002 chunk 1 (android-engineer): T-002.2 tests + T-002.3 bodies
- **Actor:** manager → android-engineer
- **Action:** Small first impl chunk per user's "small chunks" request. Added the 2 missing T-002.2 tests
  (retry-exhausted + manual-start) → `TripLifecycleStateMachineTest` 7/7. Filled T-002.3 bodies:
  `ActivityRecognitionRegistrar` (ActivityTransitionRequest IN_VEHICLE ENTER+EXIT, register/unregister,
  `MT-ActivityRecognition` logging), `ActivityTransitionReceiver` (extractResult, ENTER→seam, EXIT no-op,
  no direct trip start). Created T-002.4 seam `VehicleEntryConfidenceGateway` + no-op binding in new
  `ActivityRecognitionModule`. `./gradlew test` + `assembleDebug` both BUILD SUCCESSFUL.
- **Cost impact:** n/a ($0, on-device).
- **Follow-up:** chunk 2 = `ConfidenceAcquisitionWindowImpl : VehicleEntryConfidenceGateway` (T-002.4),
  swap the binding, wire into service's `tripLifecycleMutex` flow. Then T-002.1 permission flow. Uncommitted.

### [2026-06-22 (close)] DONE — T-002 chunk 2 (android-engineer): ConfidenceAcquisitionWindowImpl built (T-002.4)
- **Actor:** manager → android-engineer
- **Context:** Continuing the small-chunks plan from chunk 1. This chunk's scope was strictly the
  confidence-acquisition window itself — explicitly NOT the service wiring (kept as a separate future
  chunk, since the spec itself flags that as a judgment call to keep apart from the mechanical window build).
- **Action / Decision:** Built `ConfidenceAcquisitionWindow`/`ConfidenceAcquisitionWindowImpl` (running-max
  confidence tracking, 30s injected-`CoroutineScope` timer, unregister-before-emit on both terminal paths,
  re-entrant `startWindow()` no-op) per spec §4. Added `ConfidenceUpdateReceiver` (separate
  `@AndroidEntryPoint` receiver, thin parser only) and a new `ActivityUpdatesRegistrar` seam (judgment call
  beyond the spec's literal pseudocode: the real `ActivityRecognitionClient` is a concrete Play Services
  class with no public interface, so it can't be hand-faked under this project's no-mocking-framework
  convention — the seam keeps `ConfidenceAcquisitionWindowImpl` unit-testable). Rebound
  `VehicleEntryConfidenceGateway` in `ActivityRecognitionModule` from the `NoOp` placeholder to the real impl.
  **Hilt scope decision:** bound `@Singleton` (not the spec's suggested `@ServiceScoped`) — the new
  `ConfidenceUpdateReceiver`, like the existing `ActivityTransitionReceiver`, is an `@AndroidEntryPoint
  BroadcastReceiver` that can run independently of the foreground service's lifecycle, so a service-scoped
  binding would be unreachable when either receiver fires without the service's Hilt graph active. Documented
  as a no-delete-compliant strikethrough note in `team/blueprints/T-002-vehicle-detection-spec.md` §6.
  `./gradlew test`: 26 tests, 0 failures, including the 4 new spec §5 virtual-time test cases (confident-entry
  cancels timer; all-below-70-for-30s fires retry-exhausted with running max not latest; high-then-low doesn't
  erase the max; re-entrant `startWindow` is a no-op).
- **Rationale:** Keeps each chunk small and independently verifiable per the user's explicit request to limit
  loss if a session runs out of tokens mid-work.
- **Cost impact:** n/a ($0, on-device only — no new permission beyond the already-declared
  `ACTIVITY_RECOGNITION`).
- **Follow-up:** Still open for T-002: T-002.1 (permission-request screen, not built) and wiring
  `ConfidenceAcquisitionWindow.observeResults()` into `TripTrackingForegroundService` (the shared
  insert/notify tail extraction the spec flags as a judgment call, not boilerplate). Uncommitted.

### [2026-06-22 (close)] NOTE — Bluetooth detection idea: extended scope parked (T-002.5); T-020 opened for debug-only diagnostic logging
- **Actor:** manager (user request)
- **Context:** User proposed Bluetooth-paired-vehicle detection as an additional, more reliable trigger
  (named profiles `my_vehicle_1/2/3`, saved pairing config), explicitly caveated that auto-connect
  reliability varies by vehicle/head-unit (their own car requires a manual head-unit selection) and floated
  using Bluetooth connection state as ground truth to validate the other detection methods, eventually
  feeding an ML model — explicitly NOT needed for the MVP. Separately, user asked for a much smaller,
  immediate piece: log Bluetooth connection state (`true`/`false` + device name) alongside the existing
  `MT-ActivityRecognition` log lines, for their own testing only — explicitly not for field testers, since
  they have one car and testers will have differing cars/behaviours warranting separately-specified testing
  later.
- **Action / Decision:** Logged the full named-profile/detection-trigger/ML-groundwork idea as deferred
  extended scope on T-002.5 in `team/TASKS.md` (not implemented now). Opened **T-020** — debug-only
  Bluetooth connection diagnostic logging — scoped to exactly: a `bluetoothConnected: true/false` +
  device-name/id field logged alongside `MT-ActivityRecognition` lines, debug-builds-only via Gradle
  build-type source sets (`BLUETOOTH_CONNECT` permission declared only in `app/src/debug/AndroidManifest.xml`;
  release variant gets a no-op implementation, no permission, nothing logged) so field testers' builds are
  entirely unaffected. No UI permission-grant flow planned — a debug-only diagnostic tool degrades to a
  "permission not granted" log line rather than prompting. Decided directly with the user (asked via
  `AskUserQuestion` about debug-only vs. shipped-to-everyone vs. hidden-toggle; user's reply described the
  minimal field shape rather than picking a letter, read as confirming the debug-only default — confirmed
  explicitly moments later: "yes..its for debugging only"). Mirrored onto `agile/board.md` as a note under
  T-002's card rather than a 4th In-Progress card, to keep the WIP-3 limit honest (T-020 isn't a kanban-level
  user-facing feature, it rides alongside T-002's own testing).
- **Rationale:** Separates a genuinely useful, low-cost diagnostic instrument (ship now, debug-only, helps
  tune detection thresholds toward ~99-100% accuracy by cross-referencing logged confidence against a
  manually-confirmed "was I actually driving") from a much bigger, not-yet-justified feature (named vehicle
  profiles as a real detection trigger) that has real reliability caveats the user identified themselves.
- **Cost impact:** n/a ($0 — on-device only, debug-build-only, no cloud touch).
- **Follow-up:** T-020 implementation not yet started — next step is delegating to android-engineer.
  T-002.5's extended scope stays parked in `team/TASKS.md` until actually picked up.

### [2026-06-22 (close)] DONE — T-020 built: debug-only Bluetooth diagnostic logging; pre-existing manifest leak fixed
- **Actor:** manager → android-engineer
- **Action / Decision:** Built `BluetoothDiagnosticsSnapshot` (shared interface, `src/main`) with a real
  implementation in `app/src/debug/...` (dynamically-registered `BroadcastReceiver` for ACL
  connect/disconnect, held entirely inside the debug-only `@Singleton` — not a manifest receiver, so the
  "doesn't exist in release" property is contained to one file pair) and a trivial no-op implementation
  in `app/src/release/...`. `BLUETOOTH_CONNECT`/legacy `BLUETOOTH` declared ONLY in
  `app/src/debug/AndroidManifest.xml`. Wired into all 3 `MT-ActivityRecognition` log lines in
  `ConfidenceAcquisitionWindowImpl` via a new `BluetoothDiagnosticsModule` Hilt binding. Graceful
  degradation: falls back to the device MAC as the label if the permission isn't granted, one-time
  `MT-Bluetooth` warning instead of per-reading spam, `SecurityException` guarded around the actual read.
  `./gradlew clean test assembleDebug`: 6 test classes × 2 variants, 30 tests each, 0 failures.
  **Verified the release manifest has zero trace** by inspecting
  `processReleaseManifestForPackage`'s merged output directly — no `BLUETOOTH_CONNECT`/`BLUETOOTH`
  anywhere; confirmed present in the debug merged manifest as expected.
- **Real bug found and fixed (not part of the original ask):** `app/src/main/AndroidManifest.xml` already
  speculatively declared `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` from T-001 scaffolding. Left alone, this
  would have shipped the Bluetooth permission request to every field tester regardless of today's
  debug-only work — directly defeating the point of this task. Removed from `src/main`.
- **Scope note:** deliberately limited to `ConfidenceAcquisitionWindowImpl`'s 3 log lines, per the brief's
  own allowance to skip `ActivityTransitionReceiver`'s purely-informational ENTER/EXIT lines if adding the
  same wiring there added meaningful complexity for no real diagnostic gain.
- **Rationale:** Read logs before fault-finding rule paid off in reverse here — the engineer's
  verification step (actually inspecting the merged manifest rather than trusting the source files) is
  what caught the pre-existing leak; a source-only review would have missed it since the speculative
  declaration looked intentional at a glance.
- **Cost impact:** n/a ($0 — on-device only, debug-build-only).
- **Follow-up:** Not committed yet — awaiting explicit user go-ahead per standing rule. T-002 still has
  T-002.1 (permission screen) and the service-wiring chunk open.

### [2026-06-22 (close)] DONE — T-021: all-in-one dev environment startup script (devops-engineer)
- **Actor:** manager → devops-engineer
- **Context:** User asked for one idempotent PowerShell script that checks whether the Python backend
  `.venv`/Flask server and the Android emulator + app are already running, starting only what's
  missing, then always doing a fresh app reload — to stop manually re-running the same handful of
  commands every session.
- **Action / Decision:** Built `scripts/start-dev.ps1`. Backend: creates `backend/.venv` + installs
  `requirements.txt` only if absent; probes `http://127.0.0.1:8080/health` before starting
  `backend\.venv\Scripts\python.exe app.py` as a background process (no shell activation needed).
  Android: fails loudly (not silently) if `ANDROID_HOME`/`ANDROID_SDK_ROOT` is unset or invalid;
  resolves `adb`/`emulator` explicitly under `$ANDROID_HOME` rather than assuming PATH; detects an
  already-running emulator via `adb devices`, otherwise boots `test_device` in the background with a
  90s `sys.boot_completed` poll and a real timeout error. Always runs `gradlew.bat installDebug` (the
  actual reload step) and launches via `adb shell am start -n com.mileagetracker.app/.MainActivity`.
  Prints a skipped-vs-started summary; surfaces real Gradle failures rather than swallowing them.
  Added a bonus `-Stop` switch that only stops processes the script itself started (tracked via a new
  git-ignored `scripts/.dev-pids.json`).
- **Verification (actually run, not assumed):** cold start (venv created, Flask started, emulator
  detected already running, `installDebug` BUILD SUCCESSFUL, app confirmed foregrounded via
  `adb shell dumpsys activity activities` + `adb shell ps`); immediate re-run (correctly skipped
  venv/Flask/emulator as already up, while still re-running `installDebug` every time); `-Stop`
  (stopped only the Flask process the script itself started, left the pre-existing emulator
  untouched).
- **Two evidence-based deviations from the brief, both found by testing rather than guessed:**
  (1) health-check probes `127.0.0.1` instead of `localhost` — PowerShell 7's `HttpClient`
  intermittently stalls trying the IPv6 route first even though Flask binds `0.0.0.0`; (2) the
  liveness-probe catch is broadened beyond a typed `WebException` catch, because PowerShell 7 throws
  `TaskCanceledException` on timeout instead — the real exception text is still surfaced via
  `-Verbose`; this is the brief's own "check first" pattern, not error suppression.
- **Rationale:** Pure local dev tooling, explicitly devops-engineer's domain (".venv convention" +
  "local-environment setup" per `.claude/CLAUDE.md`'s agent table) — no app/backend source touched.
- **Cost impact:** n/a ($0 — local tooling only, no cloud/architecture change, no cost-architect
  ruling needed).
- **Follow-up:** Not committed yet — awaiting explicit user go-ahead per standing rule, alongside
  T-002.4/T-020 from earlier this session.

### [2026-06-22 (close)] DECISION/DONE — T-021 revised twice: ANDROID_HOME scope fallback, then kill-and-restart-fresh semantics
- **Actor:** manager → devops-engineer (two follow-up patches), manager ran the script directly each time
- **Context:** Manager ran `scripts/start-dev.ps1` for real (user asked "run it and lets see"). First
  run failed: `ANDROID_HOME`/`ANDROID_SDK_ROOT` were unset at process scope in the PowerShell tool's own
  process even though correctly set at Windows User scope (`C:\Android\Sdk`) — confirmed via
  `[Environment]::GetEnvironmentVariable(...,'User')` before touching the script. Second issue: after
  that fix, the script's idempotent "skip if already running" design meant Flask/emulator were left
  alone (already up from earlier in the session) — user watched this, didn't see the backend actually
  start, and decided the *design itself* should change to kill-and-restart-fresh every run, not just fix
  the symptom. Confirmed explicitly via `AskUserQuestion` (chose "kill and restart everything, including
  the emulator" over "backend only," after being shown the ~30-40s emulator-reboot cost) plus a same-turn
  "and emultors" follow-up.
- **Action / Decision:**
  1. **ANDROID_HOME fallback (patch 1):** script now checks Process scope first, then falls back to
     `[Environment]::GetEnvironmentVariable('ANDROID_HOME'/'ANDROID_SDK_ROOT', 'User')` then `'Machine'`,
     writing the resolved value into the current process's `$env:` for the rest of the run. Only throws
     the loud failure if genuinely unset at all three scopes. Verified by clearing process-scope vars and
     confirming fallback + by confirming the throw still fires when truly unset anywhere.
  2. **Kill-and-restart-fresh (patch 2, design change, not a bug fix):** backend now always finds and
     kills whatever's listening on port 8080 (`Get-NetTCPConnection -LocalPort 8080 -State Listen` →
     `Stop-Process`, not just the script's own in-memory PID tracking — catches processes left over from
     earlier/other runs too) before starting Flask fresh. Emulator now always kills any running
     `emulator-XXXX` (`adb emu kill`), polls `adb devices` until it's actually gone (not fire-and-forget),
     then always reboots `test_device` fresh through the existing boot-completed poll. `.venv` directory
     creation deliberately stayed create-if-missing — "start everything fresh" was read as restarting the
     *running processes*, not reinstalling pip packages on every run, which would be pure waste with no
     user benefit; not re-litigated, just applied with reasoning stated.
  3. Manager ran the script directly (not just trusting the agent's test) after both patches — first run
     reproduced the exact `ANDROID_HOME` failure live, confirming the bug was real before delegating the
     fix; final run after both patches showed the full kill→restart→reinstall→launch sequence end to end,
     with explicit log lines confirming Flask and the emulator were actually killed and restarted (PID/serial
     changed each time), `gradlew installDebug` BUILD SUCCESSFUL, app launched.
- **Rationale:** The user's first real run surfaced two genuine gaps a desk-review wouldn't have caught:
  an environment-variable-scope assumption that only breaks in certain process lineages, and a design
  assumption (idempotent skip) that didn't match what "a reload script" actually meant to the person using
  it. Both were root-caused from actual command output before any fix was attempted, per this project's
  "read the logs/output before fault-finding" rule.
- **Cost impact:** n/a ($0 — local tooling).
- **Follow-up:** T-021 now reflects kill-and-restart-fresh as the real, current behavior (not the original
  idempotent design) — `team/TASKS.md` updated to match. Still not committed; this is the 4th piece of
  uncommitted work this session alongside T-002.4/T-020/the original T-021 build.

### [2026-06-22 NOTE — Service never auto-starts: T-002 detection has no passive entry point]
- **Actor:** manager
- **Context:** User asked "are we at a point where I can put the app on my phone and we'll see
  detection logs — will it actually detect?" before sideloading. Traced the real code path instead
  of assuming the answer from the spec.
- **Action / Decision:** Confirmed by grep that `TripTrackingForegroundService` is started in exactly
  one place — `HomeStatusViewModel.onStartTripClicked()`, the manual "Start Trip" button — and
  nowhere else (no boot receiver despite `RECEIVE_BOOT_COMPLETED` being declared, no app-launch
  auto-start). `ActivityRecognitionRegistrar.register()` only runs inside that service's
  `onStartCommand`, so the whole T-002 automatic-detection pipeline (just wired this session) cannot
  fire until a trip is already manually started — at which point automatic detection has nothing
  left to do. Also confirmed `SetupPermissionsScreen`/`ViewModel` (T-002.1) is an incomplete shell:
  missing the `ACCESS_BACKGROUND_LOCATION` second-step request and never actually updates its
  granted-permission UiState flags from the launcher result.
- **Rationale:** Read the actual call graph before answering a yes/no detection question, per the
  project's "read logs/sources before fault-finding" discipline — the spec assumed the service would
  already be running and didn't address what starts it continuously.
- **Cost impact:** n/a (finding only, no code changed yet).
- **Follow-up:** logged on T-002's card in `team/TASKS.md`. Needs a decision: build a passive
  "watching" start path (e.g. start the service at app launch or via a Settings toggle, idle until
  an ENTER event) before sideloading will show real automatic detection. T-002.1's background-location
  + granted-state gaps also need closing. Awaiting user direction on priority/scope.

### [2026-06-22 DECISION — Passive-start scope for today: app-open auto-start only]
- **Actor:** manager
- **Context:** Follow-up to the previous NOTE (service never auto-starts). Presented 3 options
  (auto-start+boot-receiver / auto-start-on-open-only / settings toggle) with the battery framing
  from the T-002 spec's own §4 (Transition API registration is push-based/near-zero cost while idle;
  the expensive 5s-poll only runs inside the bounded 30s confidence window, unaffected by how the
  service starts).
- **Action / Decision:** User chose to scope today to detection working at all, explicitly via
  manually opening the app themselves — i.e. **auto-start on app open only**, no boot receiver, no
  Settings toggle yet. User's own words: "today is all about detection — we can later make it so
  its always on but sleeping, and wakes up on detection." That fuller architecture (wake from a dead
  process via the Transition API's PendingIntent broadcast, starting the foreground service only on
  actual detection rather than relying on it already being a live collector) is explicitly deferred,
  not decided against — flagged as a real future redesign, not today's task.
- **Rationale:** Matches the user's stated priority (working detection today) without over-building;
  the deferred "sleeping, wakes on detection" model is architecturally different from a continuously
  running foreground service (it would need the foreground service started directly from
  `ActivityTransitionReceiver` on ENTER, not assumed already running to collect
  `ConfidenceAcquisitionWindow.observeResults()`) and deserves its own design pass later, not a quiet
  bolt-on now.
- **Cost impact:** n/a ($0, on-device only).
- **Follow-up:** android-engineer to wire app-open auto-start (start `TripTrackingForegroundService`
  with no action from `MainActivity`/`Application.onCreate`) this session. T-002.1's background-
  location + granted-state gaps still separately open. The boot-receiver / "wake from dead process"
  redesign goes on the board as future scope when picked up, not now.

### [2026-06-23 NOTE — First sideload field findings: permission prompt silent + misleading "Detected" label]
- **Actor:** manager (relaying user's first real-device findings, not yet actioned per user request —
  "don't fix it now, just make a note")
- **Context:** User sideloaded the debug APK for the first time and tested setup before driving.
  Reviewed the exported debug log (`mileage_tracker_debug_log_20260623_082837.txt`) together —
  confirmed no crashes/errors, `MT-ActivityRecognition` registration succeeded cleanly, clean
  teardown at the end. No `IN_VEHICLE` ENTER event yet (expected — this was desk testing, not a
  drive).
- **Finding 1 — permission prompt did not fire automatically.** `SetupPermissionsScreen`'s system
  permission dialog (`ActivityResultContracts.RequestMultiplePermissions()`) did not appear when
  expected; user had to grant all permissions manually via phone Settings instead. Root cause not
  yet investigated. The repeated "Auto-starting detection service from app launch" lines (8x in
  under 2 minutes) in the reviewed log are almost certainly a side effect of this — the user
  repeatedly closing/reopening the app while working around the missing prompt, not a separate bug.
- **Finding 2 — the new "Detected"/"Not detected" status indicator (added this session) is
  mislabeled for manual starts.** It's bound to `uiState.inProgressTrip != null`, which is true for
  BOTH an automatically-detected trip AND a manually-tapped "Start trip" trip — so tapping "Start
  trip" yourself also shows "Detected," which is misleading since nothing was actually detected by
  the ActivityRecognition pipeline in that case. User's own framing: manual start is "the user
  override," and the indicator should distinguish that from real automatic detection — e.g. show
  "Manual trip" instead of "Detected" when the user is the one who tapped Start. Not yet fixed.
  Likely needs the `Trip` model (or at least in-memory state) to record which `TripStartEvent`
  variant produced it, since today nothing persists that distinction past the moment of creation.
- **Rationale:** User explicitly asked to log both for later, not fix immediately — wanted to go
  test detection in the car first.
- **Cost impact:** n/a.
- **Follow-up:** Finding 1 → investigate under T-002.1 (permission screen). Finding 2 → small UI/data
  fix riding with the status-indicator work (T-017 card) — likely needs a way to distinguish
  manual vs. automatic trip origin, not just `inProgressTrip != null`. Neither actioned yet.

### [2026-06-23 DONE — T-022 back-loop bug fixed: Home no longer re-traps Back into Classification]
- **Actor:** android-engineer
- **Context:** Logged on the T-022 card last session: `HomeStatusScreen`'s `LaunchedEffect`
  re-fired the auto-navigation to `TripClassificationScreen` on every recomposition while the
  in-progress trip stayed `PENDING_OCR` — which it does immediately after the user presses system
  Back, since nothing resolved the trip. Net effect: Back popped to Home, Home instantly bounced
  back to Classification, every single press — experienced as "Back is completely blocked."
- **Action / Decision:** Added `HomeStatusUiState.autoRoutedToClassificationTripId` (tracked in
  `HomeStatusViewModel` via a dedicated `MutableStateFlow`, merged into the existing
  `combine(...)` that builds `uiState`). `HomeStatusScreen`'s `LaunchedEffect` now calls
  `viewModel.onTripClassificationAutoRouted(tripId)` the first time it auto-navigates for a given
  trip id, and gates the auto-navigate `if` on "not already auto-routed for this trip id" — so the
  effect fires at most once per trip, not once per recomposition. Since the trip is still
  genuinely un-classified after Back (by design — work trips needing a business reason before
  export is a locked v1 fact, and a trip sitting in `PENDING_OCR` is already a tolerated state per
  T-007's pending-Work-trip CSV exclusion), Home now also exposes
  `HomeStatusUiState.showResumeClassificationAction` (true once auto-routed-but-still-pending) and
  shows an explicit "Resume classification" button in place of Start/Stop — so the trip is never
  silently stranded with no way back in to finish classifying it.
- **Rationale:** Simplest correct fix per the brief's own framing (no new screen, no settings
  toggle) — gates the existing one-shot intent rather than redesigning the navigation graph. The
  manual "Resume classification" affordance was necessary, not optional: without it, backing out
  once would have permanently stranded that trip (it can never self-resolve, and CSV export
  already deliberately excludes it while un-classified per T-007).
- **Verification:** Added `HomeStatusViewModelTest` (5 cases — gate-off-initially, gate-flips-true-
  after-auto-route, a second/different trip is NOT treated as pre-routed, manual resume doesn't
  itself mutate state, no-in-progress-trip is always ungated) plus a hand-written `FakeTripRepository`
  (first ViewModel-layer fake in this codebase). First ViewModel test in the project to need
  `Dispatchers.Main` — added `Dispatchers.setMain(StandardTestDispatcher())`/`resetMain()` via
  `@Before`/`@After`, since `viewModelScope` resolves `Dispatchers.Main` and nothing installed it
  before. `./gradlew clean test` → BUILD SUCCESSFUL, 31/31 tests pass on both debug and release
  (was 26 before this session's 5 new tests).
- **Cost impact:** n/a ($0, on-device only).
- **Follow-up:** None outstanding for this bug. T-022's logging-design entry (below) documents the
  audit trail that should make this class of bug visible from the log alone going forward.

### [2026-06-23 DECISION — T-022 audit-logging tag scheme designed + reference-implemented in HomeStatusViewModel]
- **Actor:** android-engineer
- **Context:** T-018's `MT-*` Timber/file-logging convention covers only the service/data layer
  (foreground service, ActivityRecognition, location, OCR, CSV export, Room writes) — zero
  coverage of screen-level user actions (which button was pressed, what was typed, what got
  read/written to the DB as a consequence). The back-loop bug above was diagnosed by reading
  source, not by reading the log — exactly the gap T-022 exists to close.
- **Action / Decision:** Two new tags, used uniformly across every ViewModel (not one tag per
  screen): `MT-UI` for every user-initiated action (button click, committed field value,
  ViewModel-made navigation decision), and `MT-Trip` for the DB read/write tied to that action —
  with an explicit rule that if the actual write happens in a different layer (e.g. inside
  `TripTrackingForegroundService`, not in the ViewModel that merely dispatched an `Intent`), the
  `MT-Trip` line must say so and point at which existing tag (`MT-Service`/`MT-Repository`) picks
  up the trail there, rather than implying a write happened where it didn't. Reference-implemented
  in full in `HomeStatusViewModel` (Start/Stop trip clicks + the back-loop fix's auto-route/resume
  actions) as the worked example. Full scheme, log-line shape, noise rule (log committed field
  values, not every keystroke), and a line-by-line task list for `TripClassificationViewModel` /
  `OdometerCaptureViewModel` written up in `team/blueprints/T-022-audit-logging-spec.md` for
  `android-coder` to replicate mechanically — no design decisions left for the coder to make.
- **Rationale:** Reusing T-018's existing tag/file/rotation machinery (no new logging
  infrastructure needed) keeps this cheap; two tags (not one per screen) keeps the scheme
  consistent and grep-friendly by message content rather than by an ever-growing tag list; the
  explicit "say where the real write happens" rule keeps the trail honest given this app's
  ViewModel/service boundary (T-001 blueprint §6.1) — several ViewModel actions only dispatch to
  the foreground service rather than writing the DB themselves.
- **Cost impact:** n/a ($0, on-device only).
- **Follow-up:** Hand `team/blueprints/T-022-audit-logging-spec.md` to `android-coder` for
  `TripClassificationViewModel`/`OdometerCaptureViewModel` (and the `SettingsViewModel`/
  `ExportViewModel` completeness audit). T-022 card in `team/TASKS.md` updated to reflect the
  back-loop fix as done and the logging design+reference-implementation as done, mechanical
  rollout still open.

### [2026-06-23 11:05] DONE — T-022 final review + end-to-end verification pass (android-engineer)
- **Actor:** android-engineer
- **Context:** `android-coder` mechanically applied the `MT-UI`/`MT-Trip` audit-logging convention
  (per `team/blueprints/T-022-audit-logging-spec.md` §5.1-5.3) into `TripClassificationViewModel`,
  `OdometerCaptureViewModel`, `SettingsViewModel`, `ExportViewModel`. Manager had already verified
  `./gradlew test` green (31/31, debug+release, real JUnit XML) and spot-checked
  `TripClassificationViewModel.kt`. This pass was the full diff review + the actual definition-of-done
  check: can the entire user-action + DB-write trail be reconstructed from the exported debug log
  alone, with zero source-reading.
- **Action / Decision — diff review:** Read all four files in full against the spec line-by-line.
  Every log line matches §5.1/§5.2 exactly (placement and message text, `%s`-style tripId
  interpolation, manual-entry keystroke correctly skipped per the noise rule). §5.3's
  `SettingsViewModel`/`ExportViewModel` additions cover every user-initiated action with no gaps:
  `onPhotoRetentionToggled`, `onBluetoothVehicleTriggerToggled`, `onExportDebugLogClicked`,
  `onExportRequested` all log on entry; the pre-existing `MT-Export` lines in `CsvFileWriter`/
  `DebugLogFileProvider` were left untouched, not duplicated or relocated. No non-logging behavior
  changed. No `Timber` call found in any `*Screen.kt` Composable (`HomeStatusScreen`,
  `TripClassificationScreen`, `OdometerCaptureScreen`, `SettingsScreen`, `ExportScreen` all read in
  full to confirm). No tag invented beyond `MT-UI`/`MT-Trip`. **Verdict: coder's diff was correct as
  submitted — no fixes needed.** (`ExportScreen`'s and `HomeStatusScreen`'s plain navigation-only
  buttons — Back, Trip history, Settings nav — correctly have no log line, since they're bare
  callbacks with no ViewModel method and no DB interaction; not a gap.)
- **Action / Decision — definition-of-done verification:** Used a **live emulator walkthrough**, not
  a code-trace fallback, because `test_device` (API 34) was already running and `ANDROID_HOME` was
  already configured — the stronger check specified in the task was achievable. Built
  `./gradlew assembleDebug` (BUILD SUCCESSFUL), installed via `adb install -r`, granted runtime
  permissions, then drove the UI via `adb shell input tap` + `uiautomator dump` (no Espresso harness
  exists yet, so this was the available automation path): Start Trip → Stop Trip (auto-navigated to
  Classification) → selected Work → typed business reason "ClientSiteVisit" → Save → Odometer
  Capture manual entry "45231.5" → Confirm → landed back on Home with "Last trip: 0.0 km, WORK".
  Also independently exercised `ExportScreen` (Export button → "Exported 3 trips to
  mileage_trips_20260623_103101.csv") and `SettingsScreen` (both toggles + "Export debugging logs"
  button, which itself succeeded). Then pulled the actual on-device debug log via
  `adb shell run-as com.mileagetracker.app cat .../files/logs/mileage_tracker_log.txt` — **not**
  `adb logcat`, since `FileLoggingTree` (T-018) is a deliberately file-only `Timber.Tree` that never
  calls `Log.i`/`Log.d` to logcat, only `Log.e` on its own internal write-failure path; this was
  confirmed by reading `FileLoggingTree.kt` before assuming logcat would show anything (read-logs-
  before-fault-finding rule). The pulled log fully reconstructed the walkthrough with no source-
  reading required: `MT-UI`/`MT-Trip` lines for every step, tripId threaded through all of them, and
  the Home-dispatch lines honestly stating the real DB write happens inside
  `TripTrackingForegroundService` (per the spec's "honest about the boundary" rule), not in the
  ViewModel.
- **Action / Decision — back-loop fix re-verification:** Re-ran the back-loop scenario live (not
  just re-reading `HomeStatusViewModel.kt`/`HomeStatusScreen.kt` from earlier this session): started
  a second trip, stopped it (auto-navigated to Classification), pressed system Back → landed on Home
  and **stayed there** on a second UI dump after a pause (no auto-bounce-back), showing "Last trip
  still needs classification" + "Resume classification" button; tapped it → correctly returned to
  Classification for the same trip. The pulled log shows exactly one "auto-navigated to
  TripClassification" line for that tripId followed later by one "Resume classification button
  clicked" line for the same tripId, with no repeat auto-navigation line in between — log-only proof
  the `autoRoutedToClassificationTripId` gate held.
- **Rationale:** "Compiles and tests pass" was already confirmed by the Manager; this pass exists
  specifically to test the actual product claim ("reconstructable from the log alone, no source-
  reading") rather than just re-confirming the code looks right. A live walkthrough is strictly
  stronger evidence than a code-trace for that claim, and was achievable here, so it was used instead
  of the code-trace fallback.
- **Cost impact:** n/a ($0, on-device/emulator only).
- **Follow-up:** `team/TASKS.md` T-022 card updated with the close-out note (no-delete rule applied —
  prior status lines retained). No commit/push performed this pass — awaiting explicit user
  go-ahead per the standing rule before either.

### [2026-06-23 09:10] DONE — T-003 trip-classification notification fire + lock-screen open + pending-nav UX
- **Actor:** android-engineer
- **Context:** T-003 had a built `TripAlertNotificationChannel` and a scaffolded
  `TripClassificationNotificationBuilder` from T-001, but grepping the whole `app/src/main` tree
  confirmed neither was ever called — the notification had literally never fired on a device, and
  `MainActivity` never read its launch `Intent`, had no `onNewIntent`, and `MileageTrackerNavHost`
  had no external entry point. Confirmed scope with the user first: single notification, fires on
  trip STOP into PENDING_OCR, for every trip regardless of start method — this intentionally
  supersedes the brief's literal "notify at START" wording; the already-tested classify-at-stop
  architecture (HomeStatusScreen's in-app `LaunchedEffect` auto-route, the T-022 back-loop fix)
  stays exactly as-is, not rearchitected.
- **Action / Decision:**
  1. Notification fire wired into the single shared `handleStopEvent` tail in
     `TripTrackingForegroundService` (all 3 stop paths land here) — `notifyTripAwaitingClassification`
     calls `NotificationManager.notify(tripId.hashCode(), tripClassificationNotificationBuilder.build(tripId))`
     right after `tripRepository.updateStatus`. Deterministic id = `tripId.hashCode()`, matching the
     builder's existing `PendingIntent` request-code convention, so re-stop/recovery never stacks a
     duplicate notification for the same trip.
  2. New nav seam: `PendingTripClassificationNavigationStore` (`@Singleton`, `MutableStateFlow<String?>`,
     `setPendingTripId`/`consumePendingTripId`). `MainActivity.onCreate` + new `onNewIntent(Intent)`
     override both extract `ACTION_OPEN_TRIP_CLASSIFICATION`/`EXTRA_TRIP_ID` and write into the
     store; `MileageTrackerNavHost` takes the store as an optional constructor param, observes it via
     `collectAsState()` inside `LaunchedEffect(pendingTripId)`, and calls `consumePendingTripId()`
     (clearing it) *before* navigating — so the same tripId can never be navigated to twice, including
     across a rotation (the store is Hilt-singleton-scoped and outlives the recreated Activity, but
     the cleared/consumed state still blocks replay).
  3. Lock-screen wake-and-open: `MainActivity.onCreate` calls `setShowWhenLocked(true)`/
     `setTurnScreenOn(true)` programmatically as defense-in-depth alongside the manifest's existing
     `android:showWhenLocked`/`turnScreenOn` attributes (already present, unused until now).
     Deliberately did NOT add `setFullScreenIntent` — brief §5.2 only requires wake-**on-tap**, not an
     unprompted heads-up popup over the lock screen (that's `CATEGORY_CALL`-style behavior the brief
     never asked for), and `USE_FULL_SCREEN_INTENT` carries elevated-permission/OEM-revocation risk on
     API 33+ that a tap-driven design avoids entirely. The brief's explicit fallback ("must still open
     the app normally" if lock-screen interaction is disallowed) needs no extra code — it's Android's
     own default behavior when a device's policy refuses `showWhenLocked`.
  4. Added `PendingTripClassificationNavigationStoreTest` (7 cases, plain JUnit, no Android framework
     dependency, no mocking framework per project convention).
  5. MT-UI/MT-Trip logging: added (T-022 convention). `MainActivity` logs `MT-UI` on notification tap
     (tripId) and on a malformed intent; `TripTrackingForegroundService.notifyTripAwaitingClassification`
     logs `MT-Trip` (trip-stop → notification-posted) and `MT-Service` (`.e`) on `NotificationManager`
     unavailability. T-022 itself didn't cover this because the notification never fired when T-022
     was built — this is the first real coverage of the notification-fire/tap path.
- **Rationale:** the deterministic-notification-id requirement directly prevents a real field bug
  (duplicate stacked notifications across restart-recovery/re-stop); the singleton-store nav seam was
  chosen over a `Channel`/`SharedFlow` specifically because it survives Activity recreation across
  rotation correctly while still enforcing "consumed exactly once"; the full-screen-intent question
  was resolved by re-reading brief §5.2's literal wording rather than assuming "lock-screen action"
  implies an unprompted popup.
- **Cost impact:** n/a ($0, on-device build/test only).
- **Follow-up:** `team/TASKS.md` T-003 card updated with full design + verification detail (no-delete
  rule applied). `./gradlew test` read via JUnit XML directly: debug 38/38 passed, release 38/38
  passed (8 test classes each, 0 failures/errors). `./gradlew assembleDebug` also green, confirming
  the Hilt graph resolves with the new `@Inject` sites. Not committed/pushed — awaiting explicit user
  go-ahead per the standing rule.

### [2026-06-23 09:30] NOTE — Standing reminder: debug/audit logging is mandatory on every new user-facing surface, not just T-018/T-022's original scope
- **Actor:** manager
- **Context:** user explicitly flagged mid-session, while T-003 was in flight, "please remember the
  debugging logic — its important for field tests." T-003's android-engineer had already added
  `MT-UI`/`MT-Trip` coverage for the new notification fire/tap path as part of the same change (see the
  DONE entry directly above), so nothing was missed this time — but the user's wording ("remember") reads
  as a standing instruction for future sessions, not just confirmation of this one.
- **Action / Decision:** logging this explicitly as a standing convention so it is never assumed-covered
  by default: **any new task that adds a user-facing surface (screen, button, notification, background
  trigger) must extend the `MT-*` Timber/file-log convention (T-018's sink + T-022's interaction/
  persistence trail) to cover it, as part of that task's own definition of done — not as a follow-up
  task discovered later.** This is exactly why T-022 itself exists (the user's own framing: a field
  tester hands back a log file, the Manager must be able to reconstruct what happened from it alone) —
  T-003 is the first concrete proof the convention generalizes past its original T-018/T-022 surfaces.
- **Rationale:** the project's hard deadline (working MVP by Friday) means field-test feedback has to be
  diagnosable without a live debugging session each time — a feature that doesn't log itself is a feature
  the Manager cannot debug from a tester's exported log.
- **Cost impact:** n/a.
- **Follow-up:** every future task card (T-004 onward) should be checked for this before being marked
  Done — fold "does the new surface log via `MT-*`?" into the same review gate that already checks
  `./gradlew test` counts.

### [2026-06-23 11:15] DONE — T-002.1 Setup/Permissions screen finished (real grant tracking, two-step background location, NavHost first-run bug fixed, restricted-settings theory investigated)
- **Actor:** android-engineer
- **Context:** T-002's detection pipeline was already done/tested; T-002.1 (the Setup/Permissions
  screen) was the one remaining piece, an incomplete shell per the 2026-06-22 gap-analysis entries
  above (granted flags hardcoded `false`, no background-location second-step request, an unrelated
  NavHost bug found fresh this session, and the field finding about the dialog never firing).
- **Action / Decision:**
  - **Two-step location request** (Android 10+/API 29+ requirement, not style): added
    `SetupPermissionsPlanner` (`app/src/main/kotlin/.../ui/setup/SetupPermissionsPlanner.kt`) — a
    pure Kotlin class (only depends on `Build.VERSION_CODES` ints) with three methods:
    `firstRoundPermissionsToRequest(snapshot)`, `shouldRequestBackgroundLocation(snapshot)`, and
    `isLimitedModeRequired(snapshot)`. `SetupPermissionsScreen` now requests fine/coarse
    location+camera+activity-recognition(+notifications on 33+) in one `RequestMultiplePermissions()`
    call, then — only if foreground location was actually granted and background isn't already
    granted — fires a **second**, separate `RequestPermission()` call for
    `ACCESS_BACKGROUND_LOCATION`. Bundling background location into the first call silently fails to
    grant it on API 29+, which is exactly why it was never being granted before.
  - **Real grant-state tracking:** `SetupPermissionsUiState`'s flags (fine/coarse/background
    location, camera, activity recognition, notifications) are now updated from two sources: (1)
    `SetupPermissionsViewModel.applyGrantSnapshot(PermissionGrantSnapshot)` called from both
    launcher callbacks' actual `Map<String, Boolean>` results, and (2) the same method called from a
    `LaunchedEffect(Unit)` on initial composition that reads real OS state via
    `ContextCompat.checkSelfPermission` — so a returning user who granted everything via phone
    Settings (the field-finding user's actual path) sees accurate state, not stale `false` defaults.
  - **`isLimitedModeBannerVisible` rule:** re-read brief §8's exact locked wording rather than
    guessing — "If the user disables background location **or** notification access, the app must
    degrade gracefully." `SetupPermissionsPlanner.isLimitedModeRequired` implements precisely that:
    true if background location OR notifications denied; camera denial alone does NOT trigger it
    (camera only affects OCR capture, a separate T-005 manual-fallback concern).
  - **NavHost first-run bug (found fresh this session, not previously on the board):**
    `MileageTrackerNavHost`'s `NavHost(startDestination = Screen.SetupPermissions.route)` was
    unconditional — every launch showed Setup again, even though `onSetupComplete()` already
    persists `hasCompletedFirstRunSetup` and the screen's own doc calls itself one-time/first-run.
    Fixed by adding `StartDestinationViewModel` (`ui/navigation/StartDestinationViewModel.kt`) —
    reads `SettingsRepository.observeHasCompletedFirstRunSetup()`, maps to
    `Screen.HomeStatus.route` or `Screen.SetupPermissions.route`, exposed as a `StateFlow<String?>`
    with `null` meaning "not yet resolved." `NavHost.startDestination` can only be set once at first
    composition and can't change on recomposition, so `MileageTrackerNavHost` now shows a brief
    `CircularProgressIndicator` gate until the flag resolves past `null`, then composes the real
    `NavHost` with the correct start route — no flash of the wrong screen.
  - **Restricted-settings field finding — investigated, not assumed:** confirmed against Android's
    actual documented behavior rather than trusting the working theory blindly. Android 13+
    "restricted settings" specifically blocks Accessibility Service / Notification Listener / Device
    Admin grant screens for apps installed by an untrusted source — it does **not** block the
    ordinary `ActivityCompat.requestPermissions` dialog for standard dangerous permissions
    (`ACCESS_FINE_LOCATION`, `CAMERA`, `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS`). This app
    requests none of the restricted-settings-gated permission types, so **the theory as stated does
    not explain the field finding** — ruled out, not just unconfirmed. The actual, in-code-visible
    explanation: the original shell only ever called `permissionLauncher.launch(...)` from the
    Continue button's `onClick`, never automatically on screen composition — if the field tester
    didn't tap Continue, or tapped it, denied once, and the OS later suppressed the dialog per its
    own "don't ask again after repeated denial" policy on a relaunch, that alone reads as "the dialog
    never fired" without any installer-level OS block being involved. Decision: kept the same
    tap-driven flow (forcing an automatic dialog on composition would be bad UX and isn't how
    Android's permission model is meant to be used) but added a permanently-visible in-app advisory
    line on the Setup screen ("If a permission prompt does not appear after tapping Continue, open
    phone Settings > Apps > Mileage Tracker > Permissions to grant it manually") as a safety net for
    this and any other reason a dialog might not appear — cheap, always-correct fallback instructions
    regardless of root cause.
  - **Logging:** added `MT-UI`-tagged `Timber` logs (project's now-standing convention, see the NOTE
    entry directly above) for: Continue tapped, every first-round permission's individual
    grant/deny result, and the background-location second-step outcome.
  - **Tests (hand-written fakes only, no mocking framework):** `SetupPermissionsPlannerTest` (14
    cases — first-round permission list at various grant states and SDK levels, background-location
    second-step gating including the below-API-29/already-granted/no-foreground-grant-yet cases,
    and the limited-mode rule including the "camera alone doesn't count" case);
    `SetupPermissionsViewModelTest` (5 cases, using a new hand-written `FakeSettingsRepository`);
    `StartDestinationViewModelTest` (2 cases, same fake) proving the NavHost bug fix's resolution
    logic. New `FakeSettingsRepository` added at
    `app/src/test/kotlin/.../domain/repository/FakeSettingsRepository.kt` since none existed yet.
- **Rationale:** every change traces to a confirmed gap (file read first, not assumed) or a
  documented Android platform requirement (API 29+ background-location two-step), per project rule.
- **Cost impact:** n/a ($0, on-device build/test only).
- **Follow-up:** `team/TASKS.md` T-002 card updated. `./gradlew test` read via JUnit XML directly:
  debug 59/59 passed, release 59/59 passed (11 test classes each, 0 failures/errors — the 3 new test
  classes add 14+5+2=21 new cases on top of the prior 38). `./gradlew assembleDebug`: BUILD
  SUCCESSFUL, confirms the Hilt graph resolves with the new `StartDestinationViewModel` `@Inject`
  site. Not committed/pushed — awaiting explicit user go-ahead per the standing rule. T-002.5
  (Bluetooth extended scope) remains parked/deferred, untouched by this chunk.
