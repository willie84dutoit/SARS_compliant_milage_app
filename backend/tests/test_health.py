"""Tests for the placeholder Flask service endpoints."""

import sys
from pathlib import Path

# Allow `pytest` to be run from the repo root or from backend/ without
# requiring the package to be installed.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest

from app import flask_application


@pytest.fixture
def test_client():
    flask_application.config.update(TESTING=True)
    with flask_application.test_client() as client:
        yield client


def test_health_endpoint_returns_200_and_ok_status(test_client):
    health_response = test_client.get("/health")

    assert health_response.status_code == 200
    assert health_response.get_json() == {"status": "ok"}


def test_root_endpoint_returns_service_envelope(test_client):
    root_response = test_client.get("/")
    response_body = root_response.get_json()

    assert root_response.status_code == 200
    assert response_body["status"] == "ok"
    assert response_body["error"] is None
    assert "service_name" in response_body["data"]


def test_unknown_route_returns_json_404(test_client):
    not_found_response = test_client.get("/this-route-does-not-exist")
    response_body = not_found_response.get_json()

    assert not_found_response.status_code == 404
    assert response_body["status"] == "error"
    assert response_body["error"] is not None
