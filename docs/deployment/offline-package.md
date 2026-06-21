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
```

修改：

```txt
values/values-offline.yaml
```

安装：

```bash
helm upgrade --install aegisops chart/aegisops-0.1.0.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml
```
