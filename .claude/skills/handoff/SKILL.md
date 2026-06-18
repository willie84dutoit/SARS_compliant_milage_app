---
name: handoff
description: Manager writes an end-of-session handoff — appends a dated section to team/SESSION_HANDOFF.md summarizing state, what changed, open decisions, next actions, and carried risks, so the next session resumes cold with full context. Use at the end of a work session.
---

# /handoff — write the session handoff

You are the Manager. Run this at the end of a working session.

## Steps
1. **Gather** from `team/TASKS.md` (state changes this session), `team/LOGS.md` (decisions/debates
   since the last handoff), and any open questions for the user.
2. **Append** a new dated section to `team/SESSION_HANDOFF.md` (never edit/delete old sections):
   ```
   ## Session YYYY-MM-DD — <short theme>
   ### State of the project
   ### What happened this session
   ### Open decisions / debates
   ### Next actions
   ### Watch-outs (carried risks)
   ```
3. **Cross-check:** every "In progress" task has a note; every logged DECISION is reflected;
   carried risks from the previous handoff are either resolved or restated.
4. **Report** to the user a 3–5 line summary of where things stand and the single best next step.

## Rule
The handoff must let a cold session resume without re-reading the whole chat — write it for
someone with zero memory of today.
