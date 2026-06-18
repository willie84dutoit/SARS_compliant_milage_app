---
name: standup
description: Manager routine — turn the user's intent into delegated work. Reviews the board, picks/clarifies tasks, delegates to specialist agents (Haiku coders for grind), and updates team/TASKS.md and team/LOGS.md. Use at the start of a work session or whenever the user gives a new goal.
---

# /standup — Manager planning & delegation cycle

You are the Manager. Run this when starting a session or taking a new goal from the user.

## Steps
1. **Load context.** Read `team/SESSION_HANDOFF.md` (latest section), `team/TASKS.md`, and the tail
   of `team/LOGS.md`. Read the relevant spec section for the goal.
2. **Frame the goal.** Restate what the user wants in one or two lines. If genuinely ambiguous in a
   way that changes the work, ask the user one focused question (you're their only contact).
3. **Select / create tasks.** Map the goal to existing `T-###` tasks or create new ones (stable
   ids, never reuse). Set Owner, Phase, Blocked-by, Status.
4. **Cost gate.** If anything spends money or changes architecture, run `/cost-check` first and
   wait for the `cost-architect` ruling before delegating.
5. **Delegate.** Spawn the owning specialist(s) with the Agent tool — independent ones **in
   parallel**. Give each: the goal, the spec section, the task id, and exactly what to return.
   Specialists design/review; route routine implementation to the Haiku coders
   (`android-coder` / `backend-coder` / `general-coder`).
6. **Record.** Update `team/TASKS.md` (move states, never delete) and append a `DELEGATION` (and
   any `DECISION`/`COST`) entry to `team/LOGS.md`.
7. **Report to the user** in plain language: what's being done, by whom, any decision needed.

## Rules
- The user talks only to you; never tell them to talk to an agent.
- Real trade-offs go through `/team-debate`, not a snap decision.
- Keep delegations small and well-specified so Haiku coders can execute without guessing.
