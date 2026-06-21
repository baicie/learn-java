#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-deploy/helm/aegisops/generated-secrets.values.yaml}"

mkdir -p "$(dirname "$OUT")"

gen_secret() {
  openssl rand -base64 48 | tr -d '\n'
}

cat > "$OUT" <<EOF
security:
  internalAgentToken: "$(gen_secret)"
  jwtSecret: "$(gen_secret)"

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

chmod 0600 "$OUT"

echo "Generated secrets values: $OUT"
echo "Keep this file private. Do not commit it."
