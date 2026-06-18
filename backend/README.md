# Backend — Automated Mileage Tracker (Flask, Cloud Run)

Placeholder Flask service (T-014 / US-106). The `backend-engineer` will extend this with the
real sync API, Firestore wiring, and security rules.

## Mandatory `.venv` workflow

All Python for this project — the Flask app, scripts, tooling — runs inside a **project-local
`.venv`**. It is never global and is **never committed** (`.venv/` is already in the root
`.gitignore`). Rebuild it from `requirements.txt` whenever you start fresh.

### 1. Create the virtual environment

From the `backend/` directory:

```bash
python -m venv .venv
```

### 2. Activate it

**Windows PowerShell:**

```powershell
.venv\Scripts\Activate.ps1
```

**bash / Git Bash / macOS / Linux:**

```bash
source .venv/bin/activate
```

You should see `(.venv)` prefixed in your shell prompt once active.

### 3. Install pinned dependencies

```bash
pip install -r requirements.txt
```

### 4. Run the app locally

```bash
python app.py
```

The app listens on `http://localhost:8080` (reads the `PORT` env var, defaults to `8080`).
Verify it:

```bash
curl http://localhost:8080/health
```

### 5. Run tests

```bash
pytest
```

### 6. Deactivate when done

```bash
deactivate
```

## Running via docker-compose (local dev, no real GCP project needed)

From the **project root** (not `backend/`):

```bash
docker compose up --build
```

This builds the backend image from `backend/Dockerfile` and starts a Firestore emulator
alongside it. The backend is reachable at `http://localhost:8080`; the emulator at
`localhost:8200`. The backend container connects to the emulator via the
`FIRESTORE_EMULATOR_HOST` environment variable set in `docker-compose.yml` — no service account
key or real GCP project required for local dev.

Stop everything with:

```bash
docker compose down
```

## Rebuilding `.venv` from scratch

If `.venv/` ever gets corrupted or out of sync, delete it and recreate:

```bash
rm -rf .venv          # bash
# or: Remove-Item -Recurse -Force .venv   (PowerShell)
python -m venv .venv
# activate, then:
pip install -r requirements.txt
```

## Conventions

- No secrets in this repo or in the Docker image. Local env vars / `docker-compose.yml` only;
  production secrets come from GCP Secret Manager at Cloud Run deploy time.
- The container listens on `$PORT` (Cloud Run injects this at runtime); `8080` is the local
  default.
- Keep `requirements.txt` pinned to exact versions so CI and local installs are reproducible.
