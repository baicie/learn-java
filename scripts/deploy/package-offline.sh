#!/usr/bin/env bash
#
# AegisOps 分卷离线包脚本
#
# 功能：
#   1. 打包所有镜像为 tar
#   2. 按 VOL_SIZE_MB 分卷切块（默认 4200 MB，留余量给 CD）
#   3. Helm chart + 脚本 + values 单独打包（体积小，不用分卷）
#   4. 生成一个 manifest.txt 记录每个分卷的文件名
#
# 输出结构：
#   dist/offline/aegisops-${VERSION}/
#   ├── images/
#   │   ├── aegisops-images.tar.vol001
#   │   ├── aegisops-images.tar.vol002
#   │   └── manifest.txt          ← 自动生成，记录分卷信息
#   ├── chart/
#   │   └── aegisops-*.tgz
#   ├── values/
#   │   └── values-offline.yaml
#   ├── scripts/
#   │   ├── load-offline-images.sh
#   │   ├── render-helm.sh
#   │   └── verify-offline-package.sh
#   └── README.md
#
# 刻光盘时：光盘1 刻 images/ + manifest.txt，光盘2 刻剩余 vol
# 目标机还原：cat images/aegisops-images.tar.vol* > aegisops-images.tar
#
# 用法（默认 CD 大小 4.3GB）：
#   ./scripts/deploy/package-offline.sh
#
# 自定义分卷大小（单位 MB）：
#   VOL_SIZE_MB=800 ./scripts/deploy/package-offline.sh
#

set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"
OUT_DIR="${OUT_DIR:-dist/offline/aegisops-${VERSION}}"
# CD-ROM 单张容量，4200 MB 留出文件系统开销
VOL_SIZE_MB="${VOL_SIZE_MB:-4200}"
# split -b 参数格式：N*M 表示 N 个 M 单位
SPLIT_SIZE="${VOL_SIZE_MB}M"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/images" "$OUT_DIR/chart" "$OUT_DIR/values" "$OUT_DIR/scripts"

# ── 1. 打包镜像 ────────────────────────────────────────────────────────────
IMAGE_LIST=(
  "${REGISTRY}/aiops-server:${VERSION}"
  "${REGISTRY}/aiops-worker:${VERSION}"
  "${REGISTRY}/aiops-runner:${VERSION}"
  "${REGISTRY}/aiops-agent:${VERSION}"
)

echo "[1/5] 打包镜像..."
docker save "${IMAGE_LIST[@]}" -o "$OUT_DIR/images/aegisops-images.tar"

# ── 2. 分卷切割 ────────────────────────────────────────────────────────────
echo "[2/5] 按 ${VOL_SIZE_MB} MB 分卷切割..."
rm -f "$OUT_DIR/images/aegisops-images.tar.vol"*
split -b "${SPLIT_SIZE}" \
  --numeric-suffixes=1 \
  --additional-suffix=".tar" \
  "$OUT_DIR/images/aegisops-images.tar" \
  "$OUT_DIR/images/aegisops-images.tar.vol"

NUM_VOLUMES=$(ls "$OUT_DIR/images/aegisops-images.tar.vol"* | wc -l | tr -d ' ')
rm -f "$OUT_DIR/images/aegisops-images.tar"

# 生成 manifest.txt（刻盘/还原时必须参考）
{
  echo "VERSION=${VERSION}"
  echo "NUM_VOLUMES=${NUM_VOLUMES}"
  echo "VOL_SIZE_MB=${VOL_SIZE_MB}"
  echo ""
  echo "# 镜像列表（用于 docker load）"
  for img in "${IMAGE_LIST[@]}"; do
    echo "IMAGE=${img}"
  done
  echo ""
  echo "# 分卷文件（按顺序刻盘，还原时 cat 合并）"
  for vol in $(seq -f "%03g" 1 "${NUM_VOLUMES}"); do
    echo "VOL=${vol}:aegisops-images.tar.vol${vol}"
  done
} > "$OUT_DIR/images/manifest.txt"

echo "      -> ${NUM_VOLUMES} 个分卷，manifest.txt 已生成"

# ── 3. 打包 Helm chart ─────────────────────────────────────────────────────
echo "[3/5] 打包 Helm chart..."
helm package deploy/helm/aegisops --destination "$OUT_DIR/chart"

# ── 4. 复制配置和脚本 ─────────────────────────────────────────────────────
echo "[4/5] 复制配置和脚本..."
cp deploy/helm/aegisops/values-offline.yaml "$OUT_DIR/values/values-offline.yaml"
cp deploy/offline/README.md "$OUT_DIR/README.md"
cp scripts/deploy/load-offline-images.sh "$OUT_DIR/scripts/load-offline-images.sh"
cp scripts/deploy/render-helm.sh "$OUT_DIR/scripts/render-helm.sh"
cp scripts/deploy/verify-offline-package.sh "$OUT_DIR/scripts/verify-offline-package.sh"

# ── 5. 补充分卷还原脚本（可选，方便目标机直接用）──────────────────────────
cat > "$OUT_DIR/scripts/merge-volumes.sh" <<'SCRIPT'
#!/usr/bin/env bash
#
# 分卷合并还原脚本
# 用法：./merge-volumes.sh [images目录，默认 images/]
#
set -euo pipefail

IMG_DIR="${1:-images}"
OUT="$IMG_DIR/aegisops-images.tar"

if [[ ! -f "$IMG_DIR/manifest.txt" ]]; then
  echo "manifest.txt 不存在，请确认当前目录包含 images/" >&2
  exit 1
fi

NUM_VOLUMES=$(grep '^NUM_VOLUMES=' "$IMG_DIR/manifest.txt" | cut -d= -f2)

echo "合并 ${NUM_VOLUMES} 个分卷到 ${OUT} ..."
cat "$IMG_DIR/aegisops-images.tar.vol"* > "$OUT"
echo "完成: ${OUT}"
SCRIPT

chmod +x "$OUT_DIR/scripts/"*.sh

# ── 6. 输出摘要 ───────────────────────────────────────────────────────────
echo "[5/5] 生成摘要..."
TOTAL_IMG_SIZE=$(du -sh "$OUT_DIR/images" | cut -f1)

echo ""
echo "=== 分卷离线包摘要 ==="
echo "版本: ${VERSION}"
echo "分卷数: ${NUM_VOLUMES}（每卷 ≤ ${VOL_SIZE_MB} MB）"
echo "镜像总大小: ${TOTAL_IMG_SIZE}"
echo "输出目录: ${OUT_DIR}"
echo ""
echo "分卷文件："
ls -lh "$OUT_DIR/images/aegisops-images.tar.vol"*
echo ""
echo "完整包: dist/offline/aegisops-${VERSION}.tar.gz"
echo ""
echo "刻盘建议："
echo "  光盘1: images/manifest.txt + scripts/merge-volumes.sh"
echo "         + chart/ + values/ + README.md"
echo "  光盘2+: images/aegisops-images.tar.vol*（按顺序）"
