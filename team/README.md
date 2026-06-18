# Team Workspace — Index

Everything for the manager-fronted multi-agent workflow. **You only talk to the Manager** (the
main Claude session in this project); it delegates to the specialists, runs debates, gets cost
sign-off, and keeps these records.

## How to use it (quick start)
1. Just talk to Claude normally in this project — it *is* the Manager (defined in `.claude/CLAUDE.md`).
2. Give it a goal. It will run `/standup`, delegate, and update the board + logs.
3. For trade-offs it runs `/team-debate`; for spend it runs `/cost-check`; at session end, `/handoff`.

## Manager skills (`.claude/skills/`)
| Skill | What it does |
|---|---|
| [`/standup`](../.claude/skills/standup/SKILL.md) | Turn intent into delegated work; update board + logs. |
| [`/team-debate`](../.claude/skills/team-debate/SKILL.md) | Adversarial specialist debate + cost ruling → logged decision. |
| [`/cost-check`](../.claude/skills/cost-check/SKILL.md) | Route a proposal to the cost-architect for a spend ruling. |
| [`/handoff`](../.claude/skills/handoff/SKILL.md) | Append an end-of-session handoff. |

## The team (`.claude/agents/`)
**Advisors / leads (opus/sonnet — design & review)**
- [cost-architect](../.claude/agents/cost-architect.md) — architecture + GCP cost authority _(opus)_
- [security-crypto-specialist](../.claude/agents/security-crypto-specialist.md) — signing, auth, rules, privacy _(opus)_
- [android-engineer](../.claude/agents/android-engineer.md) — Kotlin/Compose/Room/Hilt/services _(sonnet)_
- [ios-engineer](../.claude/agents/ios-engineer.md) — Swift/CoreMotion/CoreLocation/Vision _(sonnet)_
- [geo-sensors-specialist](../.claude/agents/geo-sensors-specialist.md) — sensors, GPS, geofencing, battery _(sonnet)_
- [backend-engineer](../.claude/agents/backend-engineer.md) — Flask/Cloud Run/Firestore/Storage/API _(sonnet)_
- [ml-ocr-specialist](../.claude/agents/ml-ocr-specialist.md) — ML Kit/Vision OCR, odometer parsing _(sonnet)_
- [analytics-specialist](../.claude/agents/analytics-specialist.md) — events, dashboards, SARS reports _(sonnet)_
- [compliance-qa-specialist](../.claude/agents/compliance-qa-specialist.md) — tests, acceptance, store readiness _(sonnet)_
- [devops-engineer](../.claude/agents/devops-engineer.md) — Docker, GitHub, CI, Cloud Run, .venv _(sonnet)_

**Low-cost coders (haiku — implement well-specified grind)**
- [android-coder](../.claude/agents/android-coder.md), [backend-coder](../.claude/agents/backend-coder.md), [general-coder](../.claude/agents/general-coder.md)

## Planning artefacts (this folder)
- [LOGS.md](LOGS.md) — append-only decision/event log.
- [TASKS.md](TASKS.md) — engineering task board (`T-###`).
- [SESSION_HANDOFF.md](SESSION_HANDOFF.md) — cold-start session summaries.
- [userstories/](../userstories/) — agile stories grouped into epics (`US-###`).
- [agile/board.md](../agile/board.md) — visual flow board (WIP limit 3 In Progress).

## Source specs (project root)
`automated_mileage_tracker_spec.md` · `developer_handoff_brief.md` · `post_mvp_api_plan.md` · `publisheing guide.md`
