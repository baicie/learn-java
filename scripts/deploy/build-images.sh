#!/usr/bin/env bash
#
# 本地打 AegisOps 全部镜像。
#   - server  必须用 apps/aiops-server/Dockerfile（自带 pnpm portal-build 阶段）
#   - worker  / runner 用 deploy/docker/java-app.Dockerfile + ARG 通用模板
#   - agent   用 apps/aiops-agent/Dockerfile（Python 独立栈）
#
# 用法：
#   VERSION=0.1.0 REGISTRY=aegisops ./scripts/deploy/build-images.sh
#   ./scripts/deploy/deploy-app.sh up   # 紧接 deploy-app 拉起整个 stack
#
# 注意：portal 与 console 已切换，console 不再打包；本脚本不依赖 web/console

set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

# aiops-server：专用 Dockerfile（含 portal-build 阶段，产出 static/portal 嵌入 jar）
docker build \
  -f apps/aiops-server/Dockerfile \
  --build-arg BUILD_VERSION="${VERSION}" \
  -t "${REGISTRY}/aiops-server:${VERSION}" .

# aiops-worker：通用 Java 模板
docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-worker \
  --build-arg APP_NAME=aiops-worker \
  --build-arg APP_PORT=8081 \
  -t "${REGISTRY}/aiops-worker:${VERSION}" .

# aiops-runner：专用 Dockerfile（需要装 ansible / sshpass / tini）
docker build \
  -f apps/aiops-runner/Dockerfile \
  -t "${REGISTRY}/aiops-runner:${VERSION}" .

# aiops-agent：Python 独立栈
docker build \
  -f apps/aiops-agent/Dockerfile \
  -t "${REGISTRY}/aiops-agent:${VERSION}" .

echo "Built AegisOps images with version=${VERSION}, registry=${REGISTRY}"
