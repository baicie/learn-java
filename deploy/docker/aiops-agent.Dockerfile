# syntax=docker/dockerfile:1.7

ARG PYTHON_IMAGE=python:3.12-slim-bookworm

FROM ${PYTHON_IMAGE} AS runtime

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV AIOPS_AGENT_HOST=0.0.0.0
ENV AIOPS_AGENT_PORT=8000

RUN groupadd --system aiops \
    && useradd --system --gid aiops --home-dir /app aiops \
    && mkdir -p /app \
    && chown -R aiops:aiops /app

WORKDIR /app

COPY apps/aiops-agent/pyproject.toml ./pyproject.toml
COPY apps/aiops-agent/src ./src

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir .

USER aiops

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).read()" || exit 1

CMD ["sh", "-c", "uvicorn aiops_agent.main:app --host ${AIOPS_AGENT_HOST} --port ${AIOPS_AGENT_PORT}"]
