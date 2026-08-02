---
title: AegisOps Offline Package
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-01
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
---

# AegisOps Offline Package

## 制作离线包

```bash
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/build-images.sh
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/package-offline.sh
```

产物：

```txt
dist/offline/aegisops-0.1.0.tar.gz
```

## 离线环境安装

```bash
tar -xzf aegisops-0.1.0.tar.gz
cd aegisops-0.1.0
./scripts/verify-offline-package.sh .
./scripts/load-offline-images.sh images/aegisops-images.tar registry.local/aegisops
./scripts/generate-secrets.sh values/generated-secrets.values.yaml
```

修改 `values/values-offline.yaml`，配置 PostgreSQL、可选外部依赖及其受限
`networkPolicy.egress.allowedCidrs`；空列表和全网 CIDR 会被 Helm 拒绝。离线包不需要
IdP/Keycloak 镜像或 OAuth2 client。安装前必须生成 App/Agent mTLS 证书、角色 CA、Ed25519
Grant 密钥，以及互相独立的 App/Runner 数据库凭据；身份、audience 与 scope 必须符合
`docs/api/internal-service-authentication.md`。

配置文件：

```txt
values/values-offline.yaml
```

安装：

```bash
helm upgrade --install aegisops chart/aegisops-0.1.0.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml \
  -f values/generated-secrets.values.yaml
```

`values/generated-secrets.values.yaml` 必须通过安全通道传递且不得重新打入离线包。
