"""Minimal Flask placeholder service for the Automated Mileage Tracker backend.

This is a Cloud Run-ready scaffold (T-014 / US-106) that the backend-engineer
will extend with the real sync API, Firestore wiring, and security rules.
"""

import os

from flask import Flask, jsonify

flask_application = Flask(__name__)

SERVICE_NAME = "mileage-tracker-backend"
SERVICE_VERSION = "0.1.0"


@flask_application.get("/health")
def get_health_status():
    """Liveness/readiness probe used by Cloud Run and local docker-compose."""
    return jsonify({"status": "ok"}), 200


@flask_application.get("/")
def get_service_information():
    """Root endpoint describing the service using a status/data/error envelope."""
    response_envelope = {
        "status": "ok",
        "data": {
            "service_name": SERVICE_NAME,
            "service_version": SERVICE_VERSION,
            "message": "Automated Mileage Tracker backend placeholder is running.",
        },
        "error": None,
    }
    return jsonify(response_envelope), 200


@flask_application.errorhandler(404)
def handle_not_found_error(not_found_error):
    """Explicit JSON 404 handler instead of Flask's default HTML page."""
    response_envelope = {
        "status": "error",
        "data": None,
        "error": "The requested resource was not found.",
    }
    return jsonify(response_envelope), 404


@flask_application.errorhandler(500)
def handle_internal_server_error(internal_server_error):
    """Explicit JSON 500 handler so failures never leak stack traces to clients."""
    response_envelope = {
        "status": "error",
        "data": None,
        "error": "An internal server error occurred.",
    }
    return jsonify(response_envelope), 500


if __name__ == "__main__":
    # Cloud Run injects the PORT environment variable; default to 8080 for
    # local development so the container behaves identically in both contexts.
    listen_port = int(os.environ.get("PORT", 8080))
    flask_application.run(host="0.0.0.0", port=listen_port)
