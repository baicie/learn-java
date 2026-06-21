# AegisOps Offline Package

## 内容

- images/
  - aegisops-images.tar
- chart/
  - aegisops-0.1.0.tgz
- values/
  - values-offline.yaml
- scripts/
  - load-offline-images.sh
  - render-helm.sh
  - verify-offline-package.sh

## 安装流程

1. 加载镜像

```bash
./scripts/load-offline-images.sh images/aegisops-images.tar registry.local/aegisops
```

2. 修改 values-offline.yaml 中的 Secret 和外部依赖地址。

3. 安装 Helm chart

```bash
helm upgrade --install aegisops ./chart/aegisops-0.1.0.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml
```

4. 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
```
