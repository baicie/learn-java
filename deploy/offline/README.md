# AegisOps 离线包

离线包只包含三个业务镜像：`aegisops-app`、`aiops-agent`、`aiops-runner`。PostgreSQL 由目标
环境提供；Keycloak、OAuth2 Client、JWKS、Redis、MinIO、VictoriaMetrics 与内置 Zabbix 都
不是安装前置条件。

## 目录结构

```text
aegisops-{VERSION}/
├── images/
│   ├── aegisops-images.tar.vol001
│   └── manifest.txt
├── chart/aegisops-*.tgz
├── values/values-offline.yaml
├── init/
│   ├── 001-create-roles.sql
│   └── 002-grant-runner.sql
├── scripts/
│   ├── load-offline-images.sh
│   ├── generate-secrets.sh
│   ├── merge-volumes.sh
│   ├── render-helm.sh
│   └── verify-offline-package.sh
└── README.md
```

## 安装

1. 合并分卷并加载三个业务镜像：

```bash
./scripts/merge-volumes.sh images/
./scripts/load-offline-images.sh images/aegisops-images.tar
```

2. 在隔离环境生成 mTLS 证书、Ed25519 Grant 密钥和数据库密码：

```bash
./scripts/generate-secrets.sh values/generated-secrets.values.yaml
```

生成文件包含 App/Agent 角色隔离 CA、组件证书、任务签名密钥和 App/Runner 独立数据库密码，
权限为 `0600`。私钥只允许进入 App；Agent 和 Runner 只接收公钥。

3. 由 DBA 创建 `aegisops_app`、`aegisops_runner` 账号。先使用
`init/001-create-roles.sql` 创建角色；App 完成 Flyway 后，再执行
`init/002-grant-runner.sql` 授予 Runner 最小表权限。不要把 Runner 提升为数据库 owner。

4. 设置外部 PostgreSQL 地址与受限出站 CIDR，然后安装：

```bash
helm upgrade --install aegisops ./chart/aegisops-*.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml \
  -f values/generated-secrets.values.yaml \
  --set external.postgres.host=postgres.example.internal \
  --set-string networkPolicy.egress.allowedCidrs[0]=10.10.20.0/24
```

默认部署 App + Agent；需要审批后的自动化执行时再设置：

```bash
helm upgrade --install aegisops ./chart/aegisops-*.tgz \
  -n aegisops \
  -f values/values-offline.yaml \
  -f values/generated-secrets.values.yaml \
  --set apps.runner.enabled=true
```

## 验证

```bash
./scripts/verify-offline-package.sh .
kubectl get pods -n aegisops
kubectl get svc -n aegisops
```

外部只应暴露 App 的 8080。App 内部 8443、Agent 9008 和 Runner 状态端口不得通过
LoadBalancer、NodePort 或公网 Ingress 暴露。

## 分卷

默认分卷大小为 4200 MB。可通过 `VOL_SIZE_MB` 调整：

```bash
VOL_SIZE_MB=8000 ./scripts/deploy/package-offline.sh
```

必须保留 `manifest.txt`；所有分卷应按编号放到同一 `images/` 目录后再合并。生成的
`values/generated-secrets.values.yaml` 不得写入镜像、离线包、日志或 Git。
