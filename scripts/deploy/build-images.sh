#!/usr/bin/env bash
#
# 本地构建 AegisOps 的三个业务进程镜像。
#   - aegisops-app 合并公共 API 与后台 Worker runtime
#   - aiops-agent 保持独立 Python AI runtime
#   - aiops-runner 保持独立高权限执行边界
#
# 用法：
#   VERSION=0.1.0 REGISTRY=aegisops ./scripts/deploy/build-images.sh
#   ./scripts/deploy/deploy-app.sh up   # 紧接 deploy-app 拉起整个 stack
#
# 注意：portal 与 console 已切换，console 不再打包；本脚本不依赖 web/console

set -Eeuo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

# aegisops-app：沿用启动模块路径，镜像身份统一为 aegisops-app。
docker build \
  -f apps/aiops-server/Dockerfile \
  --build-arg BUILD_VERSION="${VERSION}" \
  -t "${REGISTRY}/aegisops-app:${VERSION}" .

# aiops-runner：专用 Dockerfile（需要装 ansible / sshpass / tini）
docker build \
  -f apps/aiops-runner/Dockerfile \
  -t "${REGISTRY}/aiops-runner:${VERSION}" .

# aiops-agent：Python 独立栈
docker build \
  -f apps/aiops-agent/Dockerfile \
  -t "${REGISTRY}/aiops-agent:${VERSION}" apps/aiops-agent

echo "Built AegisOps images with version=${VERSION}, registry=${REGISTRY}"
