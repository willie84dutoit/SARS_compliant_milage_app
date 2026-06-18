# Agile Kanban

The visual flow board for user stories. **Stories** (`US-###`, see `../userstories/`) move across
columns here; the **engineering tasks** (`T-###`, see `../team/TASKS.md`) are what each story breaks into.

- `board.md` — the live board (columns below).
- The Manager updates the board during `/standup` and at `/handoff`; movements are also noted in
  `../team/LOGS.md`.

## Columns & WIP limits
| Column | Meaning | WIP limit |
|---|---|---|
| Backlog | Captured, not yet refined | — |
| Ready | Refined, acceptance criteria clear, unblocked | — |
| In Progress | Actively being built by the owning agent | **3** |
| In Review | Code/compliance review by reviewer agents | 3 |
| Done | Acceptance criteria met, verified | — |

## Rules
- Respect the WIP limit on **In Progress** — finish before starting, to control token spend and focus.
- A story enters **Ready** only when its acceptance criteria are testable and it's unblocked.
- A story reaches **Done** only after `compliance-qa-specialist` confirms its acceptance criteria.
- No-delete rule: cards are never removed; cancelled work is struck through with a reason.
