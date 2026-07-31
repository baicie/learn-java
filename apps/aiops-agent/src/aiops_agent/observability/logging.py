"""JSON structured logging with request context fields."""

from __future__ import annotations

import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any

from aiops_agent.observability.context import (
    get_request_id,
    get_tenant_id,
    get_trace_id,
)

SECURITY_EVENT_FIELDS = (
    "eventType",
    "httpStatus",
    "requiredScope",
    "serviceId",
    "severity",
    "requestPath",
)


class JsonLogFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "requestId": get_request_id(),
            "traceId": get_trace_id(),
            "tenantId": get_tenant_id(),
        }

        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)

        for field in SECURITY_EVENT_FIELDS:
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value

        return json.dumps(payload, ensure_ascii=False, default=str)


def configure_json_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonLogFormatter())

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())
