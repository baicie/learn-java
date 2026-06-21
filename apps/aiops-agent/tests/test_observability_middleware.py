"""Tests for observability middleware."""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.testclient import TestClient

from aiops_agent.observability.middleware import RequestContextMiddleware


def test_request_context_middleware_adds_headers():
    app = FastAPI()
    app.add_middleware(RequestContextMiddleware)

    @app.get("/health")
    def health():
        return {"status": "ok"}

    client = TestClient(app)

    response = client.get("/health", headers={"X-Request-Id": "req_test"})

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "req_test"
    assert response.headers["X-Trace-Id"] == "req_test"


def test_request_context_middleware_generates_request_id():
    app = FastAPI()
    app.add_middleware(RequestContextMiddleware)

    @app.get("/health")
    def health():
        return {"status": "ok"}

    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.headers["X-Request-Id"].startswith("req_")
