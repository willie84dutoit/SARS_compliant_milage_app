---
name: handoff
description: Manager writes an end-of-session handoff — appends a dated section to team/SESSION_HANDOFF.md capturing the conversation's carry-over (discussions, reasoning, user intent, asides, open threads), NOT a status snapshot. Status lives in TASKS.md/LOGS.md. Use at the end of a work session.
---

# /handoff — write the session handoff

You are the Manager. Run this at the end of a working session.

## What the handoff IS (and is NOT)
- **IS:** a narrative carry-over of the actual conversation — what we discussed, *why* choices were
  made, the user's stated intent and preferences, asides and "by the way" moments, half-finished
  trains of thought, and anything that would be lost if the chat vanished.
- **IS NOT:** a status tracker. Where tasks stand lives in `team/TASKS.md`; decisions/events live in
  `team/LOGS.md`. Do not duplicate those here.
- **Litmus test:** "Would a fresh session lose important *context or intent* without this note?" If yes, write it.

## Steps
1. **Recall the conversation**, not just the file diffs: the threads discussed, what the user asked
   for and why, what they liked/disliked, what was left hanging.
2. **Append** a new dated section to `team/SESSION_HANDOFF.md` (never edit/delete old sections):
   ```
   ## Session YYYY-MM-DD (part N) — <short theme>
   ### The through-line of the conversation
   ### What we discussed and decided (the carry-over, not just status)
   ### Open threads to pick up
   ### The agreed next request / next step
   ### Practical environment notes for next session
   ```
3. **Cross-check:** every open thread and stated user intent from this session is captured; carried
   context from the previous handoff is either resolved or restated.
4. **Report** to the user a 3–5 line summary and the single best next step.

## Rule
Write it for a future session with **zero memory of today** — so it recovers not just *where* things
are, but *why* and *what the user actually wants*.
