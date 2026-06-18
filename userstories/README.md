# User Stories

Agile user stories grouped into epics. Stories describe **user value**; the engineering tasks that
deliver them live in `../team/TASKS.md` (linked by `T-###` ids), and movement is tracked on the board
in `../agile/board.md` (by `US-###` ids).

## Story format
```
### US-### · <short title>   [Phase] [Story points]
**As a** <role> **I want** <capability> **so that** <benefit>.

**Acceptance criteria**
- [ ] Given <context> when <action> then <outcome>
- [ ] …

**Owner agent:** <specialist>  ·  **Tasks:** T-###, T-###  ·  **Status:** Backlog/Ready/In progress/In review/Done
```

## Roles
- **Driver** — the person whose trips are tracked (primary MVP user).
- **Business owner / claimant** — submits SARS mileage claims.
- **Admin / reviewer** — reviews and approves trips (Phase-2 web app).
- **Accounting integrator** — third-party system pulling approved trips (Phase-2 API).

## Epics
- `epic-01-trip-capture.md` — detection, classification, odometer, save, export (MVP).
- `epic-02-cloud-sync-compliance.md` — sync, signing, web review, SARS export, API (Phase-2+).

> No-delete rule applies: supersede stories with strikethrough or HTML comments; never remove them.
