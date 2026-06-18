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
