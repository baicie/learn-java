#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-deploy/generated-secrets.values.yaml}"

umask 077
mkdir -p "$(dirname "$OUT")"

TEMP_FILE="$(mktemp "${OUT}.tmp.XXXXXX")"
cleanup() {
  if [ -n "${TEMP_FILE:-}" ] && [ -f "$TEMP_FILE" ]; then
    rm -f "$TEMP_FILE"
  fi
}
trap cleanup EXIT HUP INT TERM

gen_secret() {
  openssl rand -base64 48 | tr -d '\n'
}

cat > "$TEMP_FILE" <<EOF
security:
  jwtSecret: "$(gen_secret)"
  zabbixWebhookSigningSecret: "$(gen_secret)"
  diagnosisGrantSecret: "$(gen_secret)"
  serviceAuth:
    clients:
      server:
        clientSecret: "$(gen_secret)"
      worker:
        clientSecret: "$(gen_secret)"
      agent:
        clientSecret: "$(gen_secret)"

external:
  postgres:
    password: "$(gen_secret)"
  redis:
    password: "$(gen_secret)"
  clickhouse:
    password: "$(gen_secret)"
  minio:
    accessKey: "aegisops"
    secretKey: "$(gen_secret)"
EOF

chmod 0600 "$TEMP_FILE"
mv -f "$TEMP_FILE" "$OUT"
TEMP_FILE=""
trap - EXIT HUP INT TERM

echo "Generated secrets values: $OUT"
echo "Keep this file private. Do not commit it."
