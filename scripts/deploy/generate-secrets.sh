#!/usr/bin/env bash
set -Eeuo pipefail

OUT="${1:-deploy/generated-secrets.values.yaml}"
APP_DNS_NAME="${AIOPS_APP_DNS_NAME:-aegisops-app}"
AGENT_DNS_NAME="${AIOPS_AGENT_DNS_NAME:-aegisops-agent}"

umask 077
mkdir -p "$(dirname "$OUT")"

TEMP_FILE="$(mktemp "${OUT}.tmp.XXXXXX")"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-secrets.XXXXXX")"
cleanup() {
  rm -f -- "${TEMP_FILE:-}"
  rm -rf -- "${WORK_DIR:-}"
}
trap cleanup EXIT HUP INT TERM

OPENSSL_EXECUTABLE=""
for candidate in \
  "${OPENSSL_BIN:-}" \
  /opt/homebrew/opt/openssl@3/bin/openssl \
  /usr/local/opt/openssl@3/bin/openssl \
  "$(command -v openssl 2>/dev/null || true)"; do
  if [ -n "$candidate" ] \
    && [ -x "$candidate" ] \
    && "$candidate" list -public-key-algorithms 2>/dev/null | grep -Fq ED25519; then
    OPENSSL_EXECUTABLE="$candidate"
    break
  fi
done
if [ -z "$OPENSSL_EXECUTABLE" ]; then
  echo "OpenSSL with Ed25519 support is required." >&2
  exit 1
fi

openssl() {
  "$OPENSSL_EXECUTABLE" "$@"
}

gen_secret() {
  openssl rand -hex 32
}

generate_ca() {
  local name=$1
  local common_name=$2
  openssl req -x509 -newkey rsa:3072 -nodes \
    -keyout "$WORK_DIR/${name}.key" \
    -out "$WORK_DIR/${name}.crt" \
    -days 1825 \
    -subj "/CN=${common_name}" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" >/dev/null 2>&1
}

generate_leaf() {
  local name=$1
  local common_name=$2
  local dns_name=$3
  local spiffe_id=$4
  local ca_name=$5
  local serial=$6
  local config="$WORK_DIR/${name}.cnf"

  cat > "$config" <<EOF
[v3_leaf]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = DNS:${dns_name},URI:${spiffe_id}
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
EOF

  openssl req -new -newkey rsa:3072 -nodes \
    -keyout "$WORK_DIR/${name}.key" \
    -out "$WORK_DIR/${name}.csr" \
    -subj "/CN=${common_name}" >/dev/null 2>&1
  openssl x509 -req \
    -in "$WORK_DIR/${name}.csr" \
    -CA "$WORK_DIR/${ca_name}.crt" \
    -CAkey "$WORK_DIR/${ca_name}.key" \
    -set_serial "$serial" \
    -out "$WORK_DIR/${name}.crt" \
    -days 825 \
    -extfile "$config" \
    -extensions v3_leaf >/dev/null 2>&1
}

write_yaml_block() {
  local indent=$1
  local file=$2
  sed "s/^/${indent}/" "$file"
}

generate_ca control-plane-ca "AegisOps Control Plane CA"
generate_ca agent-ca "AegisOps Agent CA"
generate_leaf app aegisops-app "$APP_DNS_NAME" \
  spiffe://aegisops.local/service/aegisops-app control-plane-ca 2001
generate_leaf agent aiops-agent "$AGENT_DNS_NAME" \
  spiffe://aegisops.local/service/aiops-agent agent-ca 2002
openssl genpkey -algorithm ED25519 -out "$WORK_DIR/grant-private.pem"
openssl pkey -in "$WORK_DIR/grant-private.pem" -pubout -out "$WORK_DIR/grant-public.pem"

{
  echo "security:"
  echo "  jwtSecret: \"$(gen_secret)\""
  echo "  zabbixWebhookSigningSecret: \"$(gen_secret)\""
  echo "  taskGrant:"
  echo "    keyId: task-grant-v1"
  echo "    privateKey: |-"
  write_yaml_block "      " "$WORK_DIR/grant-private.pem"
  echo "    publicKey: |-"
  write_yaml_block "      " "$WORK_DIR/grant-public.pem"
  echo "  mtls:"
  echo "    appCertificate: |-"
  write_yaml_block "      " "$WORK_DIR/app.crt"
  echo "    appPrivateKey: |-"
  write_yaml_block "      " "$WORK_DIR/app.key"
  echo "    controlPlaneCaCertificate: |-"
  write_yaml_block "      " "$WORK_DIR/control-plane-ca.crt"
  echo "    agentCertificate: |-"
  write_yaml_block "      " "$WORK_DIR/agent.crt"
  echo "    agentPrivateKey: |-"
  write_yaml_block "      " "$WORK_DIR/agent.key"
  echo "    agentCaCertificate: |-"
  write_yaml_block "      " "$WORK_DIR/agent-ca.crt"
  echo ""
  echo "external:"
  echo "  postgres:"
  echo "    appPassword: \"$(gen_secret)\""
  echo "    runnerPassword: \"$(gen_secret)\""
} > "$TEMP_FILE"

chmod 0600 "$TEMP_FILE"
mv -f "$TEMP_FILE" "$OUT"
TEMP_FILE=""
trap - EXIT HUP INT TERM
rm -rf -- "$WORK_DIR"
WORK_DIR=""

echo "Generated mTLS, task Grant and database secret values: $OUT"
echo "Keep this file private. Do not commit it."
