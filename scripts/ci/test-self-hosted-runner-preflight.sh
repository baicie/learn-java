#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aegisops-runner-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
mkdir -p "$FAKE_BIN"

write_fake() {
  local name=$1
  local body=$2
  printf '#!/usr/bin/env bash\n%s\n' "$body" > "$FAKE_BIN/$name"
  chmod +x "$FAKE_BIN/$name"
}

write_fake uname 'echo x86_64'
write_fake java 'echo '\''openjdk version "21.0.8"'\'' >&2'
write_fake mvn 'exit 0'
write_fake node 'echo v22.18.0'
write_fake pnpm 'echo 10.33.4'
write_fake python3 'echo 3.12'
write_fake docker 'exit 0'
write_fake psql 'exit 0'
write_fake shellcheck 'exit 0'

ENV_FILE="$TMP_DIR/github-env"
HOME="$TMP_DIR/home" \
PATH="$FAKE_BIN:$PATH" \
GITHUB_ENV="$ENV_FILE" \
AIOPS_NPM_REGISTRY="https://npm.internal.example/repository/npm/" \
AIOPS_PIP_INDEX_URL="https://pypi.internal.example/simple/" \
  bash "$ROOT_DIR/scripts/ci/self-hosted-runner-preflight.sh" \
    java node python docker postgres shellcheck

grep -Fq "MAVEN_OPTS=-Dmaven.repo.local=$TMP_DIR/home/.cache/aegisops-ci/maven" "$ENV_FILE"
grep -Fq "PNPM_STORE_DIR=$TMP_DIR/home/.cache/aegisops-ci/pnpm" "$ENV_FILE"
grep -Fq 'NPM_CONFIG_REGISTRY=https://npm.internal.example/repository/npm/' "$ENV_FILE"
grep -Fq 'PIP_INDEX_URL=https://pypi.internal.example/simple/' "$ENV_FILE"

if HOME="$TMP_DIR/home" \
  PATH="$FAKE_BIN:$PATH" \
  GITHUB_ENV="$TMP_DIR/invalid-env" \
  AIOPS_NPM_REGISTRY=$'https://npm.internal.example/\nINJECTED=value' \
  bash "$ROOT_DIR/scripts/ci/self-hosted-runner-preflight.sh" node; then
  echo "preflight accepted a multiline registry value" >&2
  exit 1
fi

echo "Self-hosted runner preflight tests passed."
