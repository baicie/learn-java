# AegisOps Offline Package

## 目录结构

```
aegisops-{VERSION}/
├── images/
│   ├── aegisops-images.tar.vol001    ← 分卷文件（可能多个）
│   ├── aegisops-images.tar.vol002
│   ├── ...
│   └── manifest.txt                  ← 必须！记录分卷数量和镜像列表
├── chart/
│   └── aegisops-*.tgz
├── values/
│   └── values-offline.yaml
├── scripts/
│   ├── load-offline-images.sh        ← 镜像加载（含分卷合并）
│   ├── generate-secrets.sh            ← 在目标环境生成部署密钥
│   ├── merge-volumes.sh              ← 可单独用：合并分卷
│   ├── render-helm.sh
│   └── verify-offline-package.sh
└── README.md
```

## 安装流程

### 1. 刻盘

- **光盘1（引导盘）：**
  ```
  images/manifest.txt
  images/aegisops-images.tar.vol001
  scripts/merge-volumes.sh
  chart/
  values/
  README.md
  ```
- **光盘2+（数据盘）：**
  ```
  images/aegisops-images.tar.vol002
  images/aegisops-images.tar.vol003
  ...
  ```

> manifest.txt 记录了分卷数量 NUM_VOLUMES 和每个镜像的原始名称。
> 刻盘时必须包含 manifest.txt。

### 2. 目标服务器录入

#### 方式 A：全量拷贝到硬盘再安装（推荐）

把所有文件拷贝到目标机同一目录后：

```bash
# 合并分卷（如果用的是分卷 tar）
./scripts/merge-volumes.sh images/

# 加载镜像
./scripts/load-offline-images.sh images/aegisops-images.tar

# 在目标环境生成密钥文件，并通过安全通道把三个 client secret 配置到 IdP
./scripts/generate-secrets.sh values/generated-secrets.values.yaml

# 安装 Helm chart
helm upgrade --install aegisops ./chart/aegisops-*.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml \
  -f values/generated-secrets.values.yaml
```

#### 方式 B：直接按卷加载（不用合并到硬盘）

如果硬盘空间紧张，可以在加载前按顺序合并管道到 docker：

```bash
cat images/aegisops-images.tar.vol* | docker load
```

### 3. 配置外部依赖

修改 `values/values-offline.yaml` 中的数据库、Redis、Zabbix 与 IdP 地址，并把
`networkPolicy.egress.allowedCidrs` 配置为这些外部依赖的实际受限网段。该列表为空或包含
`0.0.0.0/0`、`::/0` 时 Helm 会拒绝安装。

离线包不包含 IdP 或 Keycloak 镜像。安装前必须准备支持 OAuth2 Client Credentials 的企业
IdP/Keycloak，并创建三个独立 client：`aiops-server`、`aiops-worker`、`aiops-agent`。把
`values/generated-secrets.values.yaml` 中对应的 client secret 通过安全通道配置到 IdP；若
client 已由 IdP 创建，则用 IdP 分配的 secret 覆盖该文件中的值。三个 client 的 Token
audience 与 scope 必须按下表配置：

| client | audience | scopes |
| --- | --- | --- |
| `aiops-server` | `aiops-agent-api` | `agent:diagnose agent:resume agent:work-record` |
| `aiops-worker` | `aiops-agent-api` | `agent:diagnose agent:work-record` |
| `aiops-agent` | `aegisops-internal-api` | `evidence:read cases:read checkpoint:read checkpoint:write memory:read memory:write plugin:authorize` |

IdP 必须把表中的 audience 写入访问 Token 的 `aud` claim，把已授权 scope 写入 `scope`
claim，并签发带 `iss`、`sub`、`iat`、`exp` 的短期 JWT。server/worker 发起 Incident 诊断时
还会签发 Diagnosis Grant；该 Grant 绑定 `tenantId + incidentId + traceId`，签名密钥只允许
进入 server/worker，不得配置给 Agent。缺少 OAuth2 配置或 Diagnosis Grant 时服务必须拒绝
请求，不得退回静态 Token 或 `X-Tenant-Id` 授权。

### 4. 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
./scripts/verify-offline-package.sh .
```

## 分卷大小说明

默认按 CD-ROM 4.3GB 容量分卷（实际可用 4200 MB）。

如需调整，修改分卷大小：

```bash
# 刻 DVD（8 GB）
VOL_SIZE_MB=8000 ./scripts/deploy/package-offline.sh

# 刻 U 盘场景（16 GB）
VOL_SIZE_MB=15000 ./scripts/deploy/package-offline.sh
```

## 注意事项

- `manifest.txt` 必须保留，删除后分卷无法自动合并
- 所有脚本在目标机执行前需 `chmod +x`
- 目标机需安装 Docker
- `values/generated-secrets.values.yaml` 含敏感信息，不得写入镜像、离线包或 Git
