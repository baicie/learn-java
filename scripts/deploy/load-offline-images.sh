#!/usr/bin/env bash
#
# 加载离线镜像脚本
#
# 支持两种模式：
#   1. 单 tar（兼容旧包）：load-offline-images.sh images/aegisops-images.tar
#   2. 分卷模式：load-offline-images.sh images/ [registry.local/aegisops]
#      -> 自动读取 manifest.txt，合并分卷后 docker load
#
# 目标 registry：可选，不传则不 re-tag / push
#
set -euo pipefail

IMG_ARCHIVE="${1:-images/aegisops-images.tar}"
TARGET_REGISTRY="${2:-}"
VERSION="${VERSION:-0.1.0}"

# ── 判定是单文件还是分卷目录 ───────────────────────────────────────────────
if [[ -d "$IMG_ARCHIVE" ]]; then
  IMG_DIR="$IMG_ARCHIVE"
  IMG_ARCHIVE=""
elif [[ -f "$IMG_ARCHIVE" ]]; then
  IMG_DIR=""
else
  echo "镜像文件不存在: $IMG_ARCHIVE" >&2
  exit 1
fi

# ── 分卷合并 ──────────────────────────────────────────────────────────────
if [[ -n "$IMG_DIR" ]]; then
  MANIFEST="${IMG_DIR}/manifest.txt"

  if [[ ! -f "$MANIFEST" ]]; then
    echo "manifest.txt 不存在，不是有效的分卷包: $MANIFEST" >&2
    exit 1
  fi

  NUM_VOLUMES=$(grep '^NUM_VOLUMES=' "$MANIFEST" | cut -d= -f2)
  MERGED="${IMG_DIR}/aegisops-images.tar"

  if [[ -f "$MERGED" ]]; then
    echo "检测到已合并的镜像文件，直接使用: $MERGED"
  else
    echo "检测到分卷包，共 ${NUM_VOLUMES} 卷，合并中..."
    cat "${IMG_DIR}"/aegisops-images.tar.vol* > "$MERGED"
    echo "合并完成: $MERGED"
  fi

  IMG_ARCHIVE="$MERGED"
fi

# ── docker load ────────────────────────────────────────────────────────────
echo "加载镜像: $IMG_ARCHIVE"
docker load -i "$IMG_ARCHIVE"

# ── re-tag + push ─────────────────────────────────────────────────────────
if [[ -n "$TARGET_REGISTRY" ]]; then
  echo "打 tag 并推送到: ${TARGET_REGISTRY}"
  for image in aiops-server aiops-worker aiops-runner aiops-agent; do
    docker tag "${REGISTRY:-aegisops}/${image}:${VERSION}" \
               "${TARGET_REGISTRY}/${image}:${VERSION}"
    docker push "${TARGET_REGISTRY}/${image}:${VERSION}"
  done
fi

echo "离线镜像加载完成。"
