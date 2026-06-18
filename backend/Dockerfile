# syntax=docker/dockerfile:1

# ---- Build stage: install dependencies into a venv -------------------------
FROM python:3.12-slim AS builder

WORKDIR /build

# Keep pip quiet/fast and avoid writing .pyc files into the build layer.
ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ---- Runtime stage: small final image, no build tooling ---------------------
FROM python:3.12-slim AS runtime

# Create an unprivileged user; Cloud Run containers should never run as root.
RUN groupadd --system mileageapp && \
    useradd --system --gid mileageapp --create-home --home-dir /home/mileageapp mileageapp

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH"

COPY --from=builder /opt/venv /opt/venv

WORKDIR /app
COPY app.py ./

RUN chown -R mileageapp:mileageapp /app
USER mileageapp

# Cloud Run sets PORT at runtime; 8080 is the local-dev default.
ENV PORT=8080
EXPOSE 8080

# Shell form so $PORT is expanded by the container shell at startup,
# which is required for Cloud Run's dynamic port injection.
CMD exec gunicorn --bind 0.0.0.0:$PORT --workers 1 --threads 8 --timeout 0 app:flask_application
