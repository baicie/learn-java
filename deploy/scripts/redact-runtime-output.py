#!/usr/bin/env python3
"""Redact credentials from mixed plain-text and structured runtime logs."""

from __future__ import annotations

import os
import re
import sys


SENSITIVE_KEY = (
    r"[A-Z0-9_.-]*"
    r"(?:SECRET|TOKEN|PASSWORD|API_KEY|ACCESS[-_.]?KEY|AUTHORIZATION|DIAGNOSIS[-_.]?GRANT)"
    r"[A-Z0-9_.-]*"
)
SENSITIVE_ENV_NAME = re.compile(SENSITIVE_KEY, re.IGNORECASE)
DOUBLE_QUOTED_ASSIGNMENT = re.compile(
    rf'(?i)(?P<prefix>"{SENSITIVE_KEY}"\s*[:=]\s*")(?:(?:\\.)|[^"\\])*"'
)
SINGLE_QUOTED_ASSIGNMENT = re.compile(
    rf"(?i)(?P<prefix>'{SENSITIVE_KEY}'\s*[:=]\s*')(?:(?:\\.)|[^'\\])*'"
)
PLAIN_ASSIGNMENT = re.compile(
    rf"(?im)(\b{SENSITIVE_KEY}\s*[:=]\s*)(?:(?:bearer|basic)\s+)?[^\s,}}\]]+"
)


def redact_text(text: str) -> str:
    sensitive_values = {
        value
        for name, value in os.environ.items()
        if value and SENSITIVE_ENV_NAME.search(name)
    }
    for value in sorted(sensitive_values, key=len, reverse=True):
        text = text.replace(value, "[REDACTED]")

    text = DOUBLE_QUOTED_ASSIGNMENT.sub(
        lambda match: f'{match.group("prefix")}[REDACTED]"',
        text,
    )
    text = SINGLE_QUOTED_ASSIGNMENT.sub(
        lambda match: f"{match.group('prefix')}[REDACTED]'",
        text,
    )
    return PLAIN_ASSIGNMENT.sub(r"\1[REDACTED]", text)


def main() -> None:
    sys.stdout.write(redact_text(sys.stdin.read()))


if __name__ == "__main__":
    main()
