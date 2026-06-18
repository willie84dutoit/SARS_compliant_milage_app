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
