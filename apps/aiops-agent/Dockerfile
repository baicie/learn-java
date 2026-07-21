FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml README.md ./
COPY src ./src

RUN pip install --timeout 300 --retries 10 --no-cache-dir .

EXPOSE 9008

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=10 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:9008/health', timeout=3).read()" || exit 1

CMD ["uvicorn", "aiops_agent.main:app", "--host", "0.0.0.0", "--port", "9008"]
