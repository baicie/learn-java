---
title: AegisOps Offline Package
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-07-30
related:
  - docs/adr/0010-service-authentication-oauth2-only.md
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

修改 `values/values-offline.yaml`，配置外部依赖、IdP 端点以及这些服务的受限
`networkPolicy.egress.allowedCidrs`；空列表和全网 CIDR 会被 Helm 拒绝。离线包不包含
IdP/Keycloak 镜像；安装前必须准备支持 OAuth2 Client Credentials 的 IdP，并创建 `aiops-server`、
`aiops-worker`、`aiops-agent` 三个独立 client。client secret、audience 与 scope 必须与
`docs/api/internal-service-authentication.md` 一致。

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
