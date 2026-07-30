"""Request context stored in contextvars for async-safe propagation."""

from __future__ import annotations

from contextvars import ContextVar

request_id_var: ContextVar[str | None] = ContextVar("request_id", default=None)
trace_id_var: ContextVar[str | None] = ContextVar("trace_id", default=None)
tenant_id_var: ContextVar[str | None] = ContextVar("tenant_id", default=None)
diagnosis_grant_var: ContextVar[str | None] = ContextVar("diagnosis_grant", default=None)


def get_request_id() -> str | None:
    return request_id_var.get()


def get_trace_id() -> str | None:
    return trace_id_var.get()


def get_tenant_id() -> str | None:
    return tenant_id_var.get()


def get_diagnosis_grant() -> str | None:
    return diagnosis_grant_var.get()
