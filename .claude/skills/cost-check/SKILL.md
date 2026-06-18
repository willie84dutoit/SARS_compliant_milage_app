---
name: cost-check
description: Manager routes a proposal to the cost-architect for an architecture + spend ruling before it's committed. Use before any change that spends money or alters architecture (backend, sync, ML, infra, Cloud Run config, storage).
---

# /cost-check <proposal> — get a cost-architect ruling

You are the Manager. Use this as the money/architecture gate. Nothing that spends money or
changes architecture ships without a ruling here.

## Steps
1. **Package the proposal** for `cost-architect`: what's being built/changed, the expected
   traffic/data shape, and which GCP services it touches (Cloud Run, Firestore, Storage, ML).
2. **Spawn `cost-architect`** with the Agent tool and the package above.
3. **Receive the ruling** in its standard format (APPROVE / APPROVE-WITH-CHANGES / REJECT, cost
   @20/@1k/@10k users, drivers, required changes, free-tier-safe?).
4. **Act on it:**
   - APPROVE → proceed and delegate.
   - APPROVE-WITH-CHANGES → fold the changes into the task spec before delegating.
   - REJECT → take the cheaper alternative, or open a `/team-debate` if there's a real trade-off.
5. **Log** a `COST` entry in `team/LOGS.md` with the ruling verbatim; reference the affected task id.
6. **Report** the ruling to the user in one line (cost + verdict + any required change).

## Rule
Never wave a cost ruling through with "optimise later" — apply the named guardrail now.
