# T-010 — Backend Cost Model (Cloud Run / Firestore / Storage / ML)

> **Status:** Cost ruling delivered (Option A, design only — no infra changes, no billing link).
> **Owner:** cost-architect (report captured below verbatim; persisted by the Manager since
> cost-architect has no Write tool). **Reviewed against:** `post_mvp_api_plan.md` (Phase-2 stack +
> sync design), `developer_handoff_brief.md` §4/§5.3 (on-device ML Kit OCR; photo-retention
> default), `automated_mileage_tracker_spec.md` §2.5/§3 (sync `synced` flag; local `file://` photo
> path), `backend/app.py` (confirms current backend is a non-billable health-check stub).
> **Gates:** the decision to link billing to GCP project `<redacted-gcp-project-id>`
> (gcloud config `<redacted-gcloud-config>`). Billing is currently NOT linked — current spend is hard-$0.
> <!-- REDACTED 2026-06-19: real GCP project id / gcloud config name replaced before this repo went public. -->

---

## Ruling: APPROVE-WITH-CHANGES

**Proposal reviewed:** model the cost of the planned Phase-2 backend (Flask on Cloud Run +
Firestore + GCP Storage + on-device OCR) to gate the billing-link decision.

**Architecture view:** the planned stack is a good fit and cheap if built correctly. The dominant
risk is not raw volume — it's **misconfiguration** (Cloud Run `min-instances>0`, per-trip Firestore
listeners, unconditional photo upload). At all three tiers the workload is tiny; cost is driven
almost entirely by config choices, not user count. The standing recommendation to keep OCR
on-device must hold — adding Cloud Vision would be the single biggest unforced cost Phase-2 could
introduce, and it buys nothing here.

---

## 0. Grounding facts (from the repo, not assumed)

- **No billable backend exists today.** `backend/app.py` is a health/root-endpoint Flask stub only
  — no Firestore, no sync endpoint, no Storage client. Nothing is deployed. Current cloud spend =
  **$0**, and stays $0 until Phase-2 ships *and* billing is linked.
- **OCR is on-device.** `developer_handoff_brief.md` §4/§5.3 and `automated_mileage_tracker_spec.md`
  §2.5/§2.2 all specify Google ML Kit Text Recognition (on-device SDK). **ML/OCR cloud cost = $0.**
- **Sync is "completed trips only," idempotent, partitioned by `userId`.**
  (`post_mvp_api_plan.md` lines 52-55; spec line 73 `synced` flag.) This is the cheapest possible
  Firestore pattern if implemented as written.
- **Photos are local-first, default to a *device* path** (`imageLocallyStoredPath: "file://..."`,
  spec line 98). There is **no requirement to upload photos to GCP Storage** in the synced record.
  This is a critical cost lever — see §1C and Required Changes.

---

## 1. Cost projection by service and tier

### Assumptions (NO real usage data exists — labelled assumptions, not facts)

| # | Assumption | Value | Basis / sensitivity |
|---|---|---|---|
| A1 | Trips per active user per day | **6** (3 round-trips) | Linear lever — double it, double Firestore writes/Cloud Run requests. |
| A2 | Active-user ratio | **100%** treated as active | Conservative (overstates cost); real MAU usually 30-60%. |
| A3 | Sync batching | **1 sync call/user/day**, batched write of the day's trips | If the app syncs per-trip instead, Cloud Run requests rise 6x. |
| A4 | Firestore doc size | **~1.5 KB/trip** | ~15 small scalar fields + 2 geo points; no photo blob stored. |
| A5 | Web/dashboard reads | **2 trips/user/day** re-read by the web layer | Low for an MVP review dashboard; paginated. |
| A6 | Cloud Run per request | **~150 ms @ 1 vCPU / 512 MiB**, concurrency 80 | Small Flask write handler. |
| A7 | Odometer photo | **~300 KB after client-side compression** (raw camera ~2-4 MB) | Compression is a *required* guardrail, not optional. |
| A8 | Photo upload policy | **OFF by default** (only uploaded on opt-in; assume **15%** opt-in) | Matches the local-first `file://` schema; this is the recommended policy. |
| A9 | Region | `us-central1` / `us-east1` (free-tier-eligible) | Free tiers below assume a free-tier region. |

Derived monthly volumes (30-day month, A1xA2):

| | 20 users | 1,000 users | 10,000 users |
|---|---|---|---|
| Trips/month | 3,600 | 180,000 | 1,800,000 |
| Sync requests/month (A3) | 600 | 30,000 | 300,000 |
| Firestore writes/month (A1) | 3,600 | 180,000 | 1,800,000 |
| Firestore reads/month (A5) | 1,200 | 60,000 | 600,000 |
| Stored trip data (cumulative, 12mo) | ~65 MB | ~3.2 GB | ~32 GB |
| Photos uploaded/month (A7xA8) | 540 | 27,000 | 270,000 |

### A. Cloud Run

Free tier (per month, per billing account): **2,000,000 requests**, **360,000 vCPU-seconds**,
**180,000 GiB-seconds**, plus free egress within limits. Scales to zero ⇒ **no idle cost** with
`min-instances=0`.

| | 20 | 1,000 | 10,000 |
|---|---|---|---|
| Requests/mo | 600 | 30,000 | 300,000 |
| vCPU-sec/mo | 90 | 4,500 | 45,000 |
| GiB-sec/mo (0.5 GiB) | 45 | 2,250 | 22,500 |

All three tiers sit **far inside** the Cloud Run free tier (largest usage is 300k/2M requests =
15%, 45k/360k vCPU-sec = 12.5%). **Cloud Run cost @ all tiers ≈ $0/mo**, *provided*
`min-instances=0`.

> Guardrail math: a single always-on instance (`min-instances=1`) at 1 vCPU/512 MiB runs ~2.6M
> vCPU-sec/month — blows the free 360k allowance ~7x, landing around **~$45-50/mo flat**
> regardless of users. This is the #1 Cloud Run cost trap; reject any config that sets it without
> justification.

### B. Firestore (Native mode)

Free tier (per project, per **day**): **50,000 document reads**, **20,000 writes**, **20,000
deletes**, **1 GiB stored**, plus **10 GiB/mo egress**. (Daily, not monthly — confirmed pattern,
but re-verify exact current quotas against the live console before billing-link, see §3.)

| | 20 | 1,000 | 10,000 |
|---|---|---|---|
| Writes/day | 120 | 6,000 | 60,000 |
| Reads/day | 40 | 2,000 | 20,000 |
| Stored (12mo cumulative) | 65 MB | 3.2 GB | 32 GB |

- **20 & 1,000 users:** writes/reads well under daily free limits; storage under/near 1 GiB.
  **≈ $0/mo.**
- **10,000 users:** writes = **60,000/day vs 20,000 free** ⇒ 40,000 billable writes/day ≈
  **$40-65/mo**. Reads (20k) sit at the free edge. Storage 32 GB vs 1 GiB free ⇒ ~31 GB billable
  ≈ **$5.50/mo**.

| Firestore $/mo | 20 | 1,000 | 10,000 |
|---|---|---|---|
| | ~$0 | ~$0 (storage may tip ~$0.40) | **~$45-70** |

The first ceiling hit is the **20,000 writes/day** free limit — crossed at roughly **~3,300 active
users** under A1 (6 trips/day, per-trip writes). If trips batch into one document-write per sync
instead of per-trip, that ceiling moves out ~6x (to ~20,000 users). **This is the single
highest-leverage design choice in the whole model.**

### C. GCP (Cloud) Storage — odometer photos

Free tier (per month, `us-*` regions): **5 GB-month standard storage**, **5,000 Class-A ops**
(writes), **50,000 Class-B ops** (reads), **1 GB/mo egress to internet** (egress allowance is
small and region-nuanced — flagged in §3).

With photo upload **OFF by default + 15% opt-in + 300 KB compressed** (A7/A8):

| | 20 | 1,000 | 10,000 |
|---|---|---|---|
| Photos uploaded/mo | 540 | 27,000 | 270,000 |
| Write ops/mo (Class A) | 540 | 27,000 | 270,000 |
| New storage/mo | ~160 MB | ~8 GB | ~80 GB |
| Cumulative storage (12mo, no lifecycle) | ~1.9 GB | ~95 GB | ~950 GB |

| Storage $/mo | 20 | 1,000 | 10,000 |
|---|---|---|---|
| | ~$0 | ~$2-3 | **~$3 (mo1) → ~$23+ (mo12), unbounded without lifecycle** |

> If photo upload were **ON by default** (the wrong choice) at 300 KB/100% capture, month-12
> storage at 10k users would be ~6.4 **TB** ≈ **~$150/mo and climbing**, plus far higher egress.
> The default toggle state alone is worth ~$120+/mo at 10k users.

### D. ML / OCR

On-device Google ML Kit Text Recognition runs on the handset — no image leaves the device for
OCR, no API call is billed. **ML/OCR cost @ all tiers = $0/mo. Confirmed — keep it on-device.**

Cost *if* a cloud step were added (contrast only, to justify not doing it): Cloud Vision
`TEXT_DETECTION` is free for the first 1,000 units/month, then ~$1.50/1,000 images:
- 20 users: 3,600 OCR/mo → ~$3.90/mo
- 1,000 users: 180,000/mo → **~$268/mo**
- 10,000 users: 1,800,000/mo → **~$2,700/mo**

Cloud Vision would be, by an order of magnitude, the **most expensive service in the entire
system** and would also break the privacy/local-first posture. **Standing recommendation: OCR
stays on-device for Phase-2.**

---

## Combined cost — total/mo and per-user/mo

(Recommended config: `min-instances=0`, batched writes, photo-upload OFF by default, on-device
OCR. Figures are month-12 steady state; ranges reflect region pricing + assumptions above.)

| Tier | Cloud Run | Firestore | Storage | ML/OCR | **Total/mo** | **Per-user/mo** |
|---|---|---|---|---|---|---|
| **20 users** | $0 | $0 | $0 | $0 | **~$0** (free tier) | **~$0.00** |
| **1,000 users** | $0 | ~$0-0.4 | ~$2-3 | $0 | **~$2-4** | **~$0.002-0.004** |
| **10,000 users** | $0 | ~$45-70 | ~$3→$23+ | $0 | **~$50-95** (mo12, grows w/ photo storage) | **~$0.005-0.010** |

Headline: even at 10,000 users, all-in cost lands around **half a cent to one cent per user per
month** — *if* the four guardrails hold (below). Get one wrong and the picture changes materially:
a stray `min-instances=1` adds ~$45/mo flat; ON-by-default photos add ~$120+/mo at 10k; Cloud
Vision adds ~$2,700/mo at 10k.

---

## 2. Assumptions, ranked by cost impact

1. **A8 photo-upload policy (OFF + 15% opt-in)** — drives Storage cost and egress; the biggest
   single lever after avoiding cloud OCR.
2. **A3 sync batching (1 write/user/day vs per-trip)** — sets when the Firestore 20k-writes/day
   free ceiling is hit (~3.3k users vs ~20k users).
3. **A1 trips/user/day (6)** — linearly scales Firestore writes and Cloud Run requests.
4. **A7 photo size (300 KB compressed)** — linear on Storage GB and egress.
5. **A2 active ratio (100%)** — deliberately conservative; real cost is likely lower.

If the team gets even a week of beta telemetry, A1/A2/A8 should be replaced with measured values
and this model re-run before billing scales past closed testing.

---

## 3. Free-tier-safe defaults and where each tier breaks

**Config that keeps cost ≈ $0:**
- **Cloud Run:** `min-instances=0`, capped `max-instances` (e.g. 2-4 for MVP), `concurrency=80`,
  smallest workable image, 512 MiB / 1 vCPU.
- **Firestore:** batched writes (one `WriteBatch` per sync, not per trip), paginate all dashboard
  queries (`limit()` + cursor), index only what's queried, never attach per-trip realtime
  listeners on the web side. Use the `synced` flag to avoid re-reading the whole collection.
- **Storage:** photo upload OFF by default; client-side compress to ≤300 KB before upload;
  lifecycle rule to Nearline/Coldline after 30-90 days, delete after the legal retention window;
  never serve full-res images to the dashboard (thumbnails / signed-URL on demand).
- **OCR:** on-device only.

**Where each free tier first breaks (under the assumptions):**

| Free tier | Limit | First service to bite | Approx. user count where exceeded |
|---|---|---|---|
| Cloud Run requests | 2,000,000/mo | Requests | ~67,000 users (per-trip sync) — not binding |
| Cloud Run compute | 360,000 vCPU-sec/mo | vCPU-sec | ~48,000 users — not binding |
| **Firestore writes** | **20,000/day** | **Writes** | **~3,300 users (per-trip) / ~20,000 (batched)** ← binding constraint |
| Firestore reads | 50,000/day | Reads | ~25,000 users (at A5) |
| Firestore storage | 1 GiB | Stored docs | ~300 users (12mo cumulative); cost is cents either way |
| Storage GB | 5 GB-mo | Photo storage | ~600 users cumulative at 15% opt-in (sooner if ON-by-default) |
| Storage write ops | 5,000/mo | Class-A ops | ~185 users (at 15% opt-in); overage is pennies |

**Bottom line for the billing-link decision:** at the **20-user Play closed-testing minimum,
every service is comfortably inside the free tier — projected spend is ~$0.** Real money only
appears around ~3,000+ users on Firestore writes (deferred to ~20k with batched writes), and as
cumulative photo storage grows without lifecycle rules.

**Numbers flagged as not fully confident — verify against the live GCP console/pricing page
before linking billing:**
- Exact **Firestore daily free quotas** (50k read / 20k write / 20k delete / 1 GiB) — standard
  published figures, but confirm for the project's actual region.
- **Cloud Storage egress free allowance** (~1 GB/mo internet egress and its regional nuances) —
  the murkiest line item, easiest to under-estimate if the dashboard serves full images.
- Per-unit **Firestore write price** and **Storage GB-month price** vary by region (~$0.108-0.18
  /100k writes; ~$0.020-0.026/GB-mo) — quoted as ranges; pin the exact region before committing.

---

## 4. Safeguards for when billing is linked to `<redacted-gcp-project-id>`

Billing is currently unlinked, so spend is hard-capped at $0 today. The moment it links, that
protection disappears — put these in place **in the same change that links billing**, before any
service is deployed:

1. **Cloud Billing budget + alerts (do this first).** Budget scoped to `<redacted-gcp-project-id>`
   with email alerts at **50% / 90% / 100%**. Set the MVP budget low — e.g. **$10/mo** — so any
   unexpected charge is loud immediately. Budgets alert; they do **not** auto-stop spend by
   default — state this explicitly so no one assumes otherwise.
2. **A real kill-switch, not just alerts.** Wire the budget's Pub/Sub notification → a Cloud
   Function that disables billing on the project (or disables the offending API) at the 100%
   threshold. This is the only thing that actually *stops* runaway spend.
3. **Cap the services most able to run away, by service:**
   - **Cloud Run:** small `max-instances` (2-4 for MVP), `min-instances=0`.
   - **Firestore:** no native spend cap — protect via security rules (users write only their own
     `userId` partition), App Check (block non-app clients), and the budget kill-switch.
   - **Storage:** lifecycle rules cap storage growth; upload OFF by default; serve
     thumbnails/signed URLs to bound egress.
4. **Quota caps as a hard ceiling** where GCP allows it (e.g. Firestore writes/day, Cloud Run
   requests/min) below the point where cost gets uncomfortable — quotas hard-stop rather than
   just alert, appropriate for "fail safe and cheap" over "scale silently and bill."
5. **Do NOT enable Cloud Vision / any paid ML API** on the project while OCR is on-device. An
   un-enabled API can't be billed by accident.
6. **Re-run this model with real telemetry** before scaling past closed testing, and again before
   public launch.

**Recommendation on the billing-link decision itself:** linking billing is **safe to proceed**
for Phase-2 dev/closed-testing *only if* safeguards 1, 2, and 3 land in the same change. At
20-user testing scale, projected bill is ~$0 and the free tier absorbs it; the budget +
kill-switch exist to catch *misconfiguration*, not expected load. **Do not link billing "bare"
(alerts only) — the kill-switch is the non-negotiable guardrail.**

---

## Required changes (conditions on the APPROVE)

- Sync must **batch** the day's trips into one `WriteBatch` per user, not write per-trip — moves
  the Firestore free-tier ceiling from ~3.3k to ~20k users.
- Photo upload to GCP Storage must be **OFF by default** (preserve the local `file://` model);
  upload only on explicit cloud-retention opt-in, **compressed to ≤300 KB client-side**, with
  **lifecycle rules** from day one.
- Cloud Run must deploy with **`min-instances=0`** and a small `max-instances` cap.
- OCR stays **on-device**; Cloud Vision API stays **disabled** on the project.
- Billing-link must ship **with** a budget ($10/mo), 50/90/100% alerts, and a **Pub/Sub→Cloud
  Function billing kill-switch** — not alerts alone.

**Free-tier safe?** Yes at 20 and 1,000 users (~$0-4/mo). No at 10,000 users — the limit that
bites first is **Firestore 20,000 writes/day** (~3.3k users per-trip, ~20k batched), followed by
**Cloud Storage 5 GB-month** as photos accumulate without lifecycle rules.

**Relevant files reviewed (absolute paths):**
- `post_mvp_api_plan.md` — Phase-2 stack + sync design (lines 30-55).
- `developer_handoff_brief.md` — §4 on-device ML Kit OCR; §5.3 photo-retention default-ON-locally.
- `automated_mileage_tracker_spec.md` — §2.5 sync/`synced` flag (line 73); §3 data schema incl.
  local `file://` photo path (lines 78-104).
- `backend/app.py` — confirms backend is a non-billable health-check stub (no Firestore/
  Storage/sync wiring yet).
