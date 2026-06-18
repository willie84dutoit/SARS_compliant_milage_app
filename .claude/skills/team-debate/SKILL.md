---
name: team-debate
description: Manager runs an adversarial debate among the relevant specialists on a real trade-off, gets a cost-architect ruling, then synthesizes a decision and logs it. Use when there's a genuine trade-off (cost vs UX, accuracy vs battery, build vs buy, scheme A vs B) rather than an obvious default.
---

# /team-debate <topic> — structured multi-specialist debate

You are the Manager. Use this only for genuine trade-offs, not for choices with an obvious default.

## Procedure
1. **Frame the question** crisply: the decision, the options (2–4), and the constraints
   (locked v1 facts, cost, store/SARS compliance). Append a `DEBATE` opener to `team/LOGS.md`.
2. **Round 1 — positions (parallel).** Spawn the relevant specialists with the Agent tool, each
   asked to argue **for a specific option** and to attack the others. Give each the same framing +
   the relevant spec section. Pick the agents that actually own the trade-off, e.g.:
   - battery vs accuracy → `geo-sensors-specialist` vs `android-engineer`
   - signing scheme A vs B → `security-crypto-specialist` (both schemes) + `backend-engineer` (verify cost)
   - on-device vs cloud ML → `ml-ocr-specialist` vs `cost-architect`
3. **Round 2 — rebuttal (optional).** If positions clash on facts, spawn again passing each side
   the other's argument; ask for a focused rebuttal. Keep it to one extra round.
4. **Cost ruling.** Run `/cost-check` on the leading option(s) — `cost-architect` returns the
   per-user/month numbers and an APPROVE / APPROVE-WITH-CHANGES / REJECT.
5. **Synthesize & decide.** As Manager, weigh the arguments + cost ruling and make the call. State
   what you chose, why, what you rejected, and the conditions/guardrails.
6. **Log it.** Append a `DECISION` entry to `team/LOGS.md` capturing the options, key arguments,
   cost ruling, the decision, and follow-up task ids. Update `team/TASKS.md`.
7. **Report** the decision to the user in plain language, with the one-line "why".

## Rules
- Specialists debate; **the Manager decides** — don't outsource the call.
- Anything with a cost dimension must include the `cost-architect` ruling before deciding.
- Every debate ends in a logged, dated decision — no open-ended discussions.
