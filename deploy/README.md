# deploy — 生产 / 预发部署

本目录承载 AegisOps 的 Core Compose、Kubernetes 与离线部署产物。

## 边界

```text
infra/    ── 完整本地演示基础设施
deploy/   ── Core Compose + Helm chart + Dockerfile + 离线包

两个目录职责严格分离，绝不互相替代：
- 最小部署与基础开发用 deploy/docker-compose.core.yml
- 完整演示基础设施用 infra/docker-compose.yml
- 生产部署用 deploy/helm/aegisops/
- 离线交付用 deploy/offline/
```

## 目录结构

```text
deploy/
├─ docker/                          # 各 app 的 Dockerfile
│  ├─ java-app.Dockerfile           # 通用 Java 镜像模板（ARG: APP_MODULE / APP_NAME / APP_PORT）
│  └─ aiops-agent.Dockerfile        # aiops-agent（Python 服务）
│
├─ apps/<app-name>/Dockerfile       # 实际生产的镜像入口（deploy.yml / docker.yml 引用）
│
├─ helm/aegisops/                   # Helm chart
│  ├─ Chart.yaml
│  ├─ values.yaml                   # 默认值（仅占位）
│  ├─ values-private.yaml           # 私有化部署覆盖值（git 忽略）
│  ├─ values-offline.yaml           # 离线环境覆盖值
│  ├─ templates/                    # K8s 资源模板
│  │  ├─ deployment.yaml
│  │  ├─ service.yaml
│  │  ├─ configmap.yaml
│  │  ├─ secret.yaml                # 必须走 Secret 引用外部密钥，禁止明文
│  │  ├─ ingress.yaml
│  │  ├─ networkpolicy.yaml
│  │  ├─ serviceaccount.yaml
│  │  ├─ servicemonitor.yaml
│  │  ├─ prometheusrule.yaml
│  │  └─ grafana-dashboard.yaml
│  └─ tests/                        # helm unittest 用例
│     ├─ test_helm_values.py
│     ├─ test_observability_values.py
│     └─ test_offline_values.py
│
├─ docker-compose.core.yml          # 默认三容器 Core；可选能力使用 profiles
├─ docker-compose.app.yml           # 现有腾讯云完整 AI/Automation 发布描述符
├─ docker-compose.zabbix.yml        # 腾讯云 VM 可选 Zabbix 测试环境（Web 端口 8083）
│
├─ tests/                           # 跨 helm chart 与 dockerfile 的 Python 测试
│  └─ test_deploy_scripts.py        # 校验 deploy 脚本、镜像清单、Dockerfile 行为
│
└─ offline/                         # 离线环境交付物
   ├─ README.md
   ├─ images.txt                    # 必须打包的镜像清单
   └─ *.tar / *.tgz                 # 实际产物（构建期生成，git 忽略）
```

## Core 与可选档位

默认 Core 不需要 Keycloak、Redis、MinIO、VictoriaMetrics、Agent、Runner 或内置 Zabbix：

```bash
docker compose -f deploy/docker-compose.core.yml up -d --wait
```

本地首次构建镜像时增加 `--build`。Server 镜像已经内嵌 Portal，Core 不启动独立前端容器。

可选能力必须同时启动对应 profile 并打开应用开关：

```bash
# AI：OAuth2 Client Credentials + Diagnosis Grant + 内部 Agent API
AIOPS_AGENT_ENABLED=true \
AIOPS_INTERNAL_AGENT_API_ENABLED=true \
docker compose -f deploy/docker-compose.core.yml --profile ai up -d --wait

# Automation：Runner 继续保持独立进程
docker compose -f deploy/docker-compose.core.yml --profile automation up -d --wait

# 对象存储 / 观测 / Redis 分别按需启用
AIOPS_OBJECT_STORAGE_ENABLED=true \
docker compose -f deploy/docker-compose.core.yml --profile object-storage up -d --wait

AIOPS_EVIDENCE_VICTORIA_ENABLED=true \
docker compose -f deploy/docker-compose.core.yml --profile observability up -d --wait

AIOPS_QUOTA_BACKEND=redis \
AIOPS_REDIS_HEALTH_ENABLED=true \
docker compose -f deploy/docker-compose.core.yml --profile distributed-cache up -d --wait

# 完整 Demo：Core + AI + Runner + Redis + MinIO + VictoriaMetrics + Zabbix
AIOPS_AGENT_ENABLED=true \
AIOPS_INTERNAL_AGENT_API_ENABLED=true \
AIOPS_OBJECT_STORAGE_ENABLED=true \
AIOPS_EVIDENCE_VICTORIA_ENABLED=true \
AIOPS_QUOTA_BACKEND=redis \
AIOPS_REDIS_HEALTH_ENABLED=true \
docker compose -f deploy/docker-compose.core.yml --profile demo up -d --wait
```

生产启用 AI 时应把 Compose 内的开发 Keycloak 替换为企业 IdP，并注入独立 client secret。
关闭能力时应用使用 fail-closed 实现，不会降级为静态 token、无鉴权 HTTP 或本地文件存储。

## 完整发布拓扑

```text
生产 VM（部署单元）：
  postgres  ── 5432  ──┐
                        ├── aiops-server   (8080, 控制面, Spring Boot)
                        ├── aiops-worker   (8081, 异步消费 + RCA/Incident 聚合)
                        └── aiops-runner   (8092, 隔离执行 ansible / webhook)

  aiops-agent  (9008, Python LangGraph) ── 通过服务 JWT + Diagnosis Grant 调用 Java internal API
```

`docker-compose.app.yml` 保留为现有腾讯云完整 AI/Automation 发布描述符，不再代表默认安装。

说明：

- worker / runner 跟 server 共享同一 Postgres（独立 schema 与表，互不耦合）
- runner 镜像额外装了 `ansible-playbook` / `sshpass` / `openssh-client` 与 `tini`
- 完整发布档的 worker 会调用 agent 执行诊断与工作记录生成；runner 不依赖
  agent（agent 的 canonical workflow 只查 server，不直接调 runner）

## 低内存 VM 配置

`docker-compose.app.yml` 默认按 4 GiB、低并发单机部署约束资源。三个 Java 进程使用独立的
`-Xmx`、Metaspace、Direct Memory 和 Code Cache 上限，并用 Serial GC 减少小堆场景下的
GC 线程与本地内存开销。Server、Worker、Runner 仍保持独立进程，不能为了节省内存破坏
Runner 执行隔离边界。

默认硬限制如下：

| 容器 | 内存限制 | JVM 最大堆 |
| --- | ---: | ---: |
| postgres | 384 MiB | - |
| redis | 128 MiB | - |
| aiops-server | 640 MiB | 320 MiB |
| aiops-worker | 576 MiB | 256 MiB |
| aiops-runner | 512 MiB | 192 MiB |
| aiops-agent | 256 MiB | - |

所有值均可通过环境变量覆盖，例如：

```bash
export AIOPS_SERVER_MEMORY_LIMIT=768m
export AIOPS_SERVER_JAVA_OPTS='-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=96m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError'
```

部署前后使用以下命令核对，不要只根据 Linux `used` 判断是否存在内存压力：

```bash
free -h
docker stats --no-stream
docker inspect -f '{{.Name}} {{.HostConfig.Memory}} {{.HostConfig.MemoryReservation}}' $(docker ps -q)
```

4 GiB 主机必须避免同时运行无关的 ClickHouse、测试实验栈和其他业务项目。生产主机还应配置
至少 2 GiB Swap 作为峰值保护；创建 Swap 属于宿主机变更，应通过单独审批和运维窗口执行。
若诊断、导出或自动化任务出现长时间 GC、容器 OOM，应先按实际指标提高对应单个容器限制，
不要取消全部限制。

## VM 发布描述符状态

腾讯云 VM 发布使用三个同目录文件管理版本状态：

- `docker-compose.app.yml`：active，只记录最后通过容器健康检查和双向 OAuth2 探针的发布。
- `docker-compose.app.candidate.yml`：candidate，由发布 workflow 上传，本次部署只读取该文件。
- `docker-compose.app.previous.yml`：previous，在 candidate 校验通过后从 active 原子快照得到。

workflow 不直接覆盖 active。拉镜像、端口预检、新版本健康检查、OAuth2 探针或自动回滚任一
阶段失败时，active 都继续指向最后验证成功的发布；只有全部检查通过后，candidate 才在同一
目录内原子提升为 active。生产 deploy job 固定串行执行，避免手动发布与自动发布竞争同一个
candidate 文件。

## 镜像名 / Tag 约束

- 所有镜像统一命名空间：`aegisops/<app-name>:<version>`，前缀由 deploy.yml 注入为 `${DOCKERHUB_USERNAME}/aegisops`
- 当前约定版本：`0.1.0`
- 修改任何镜像名 / Tag，**必须同步修改**：
  - `infra/docker-compose.yml`
  - `deploy/offline/images.txt`
  - `deploy/docker-compose.app.yml`
  - `apps/<app-name>/Dockerfile`
- CI 中的 `deploy/tests/test_deploy_scripts.py::test_offline_image_list_contains_required_images` 会校验镜像清单一致性
- `deploy.yml` 给每个镜像打以下 tag（长 SHA 用于按 commit 回滚）：
  - `aegisops/aiops-server` → `git-<sha7>` / `<sha40>` / `<branch>` / `latest`(仅 main)
  - `aegisops/aiops-agent` → `git-<sha7>` / `<sha40>` / `<branch>` / `latest`(仅 main)
  - `aegisops/aiops-worker` → `git-<sha7>` / `<sha40>` / `<branch>` / `latest`(仅 main)
  - `aegisops/aiops-runner` → `git-<sha7>` / `<sha40>` / `<branch>` / `latest`(仅 main)

## 密钥引用方式

- 所有密钥经 Helm `secretKeyRef` 引用外部 Secret 对象
- server、worker、agent 使用独立 OAuth2 client secret；Diagnosis Grant 密钥只进入
  server/worker
- Helm 与 Compose 只支持 OAuth2 Client Credentials；server、worker、agent 使用不同
  client secret
- 推荐外部密钥源：阿里云 KMS / HashiCorp Vault / AWS Secrets Manager
- CI 端：在 GitHub Secrets 配置对应键，由部署脚本注入到 Secret 对象
- 严禁任何明文密钥出现在 `values.yaml`、`templates/*.yaml`、`Dockerfile` 中

## NetworkPolicy 出站白名单

启用 `networkPolicy.enabled=true` 后，chart 会为各组件同时启用 Ingress/Egress 默认拒绝，
再显式放行 server/worker → agent、agent → server、DNS 和外部依赖流量。生产部署必须
显式配置 `networkPolicy.egress.allowedCidrs` 为企业 IdP、PostgreSQL、Redis、
ClickHouse、MinIO、VictoriaMetrics 等实际目标网段。空列表以及 `0.0.0.0/0`、`::/0`
会在 Helm 渲染阶段被拒绝。

`networkPolicy.egress.externalPorts` 按组件声明可访问的外部端口。删除未使用的端口；Agent
通常只需要到 IdP 的 `443`，不应开放数据库、Redis、ClickHouse 或 VictoriaMetrics 端口。
标准 Kubernetes NetworkPolicy 不能按 DNS 名过滤，因此域名解析单独由
`dnsNamespaceSelector` 放行，实际目标仍必须由 CIDR 与端口共同限制。

## 本地验证

```bash
# 1. 单元测试 helm chart
helm unittest deploy/helm/aegisops

# 2. 校验 chart 语法
helm lint deploy/helm/aegisops --values deploy/helm/aegisops/values.yaml

# 3. 渲染模板（debug 用）
helm template aegisops deploy/helm/aegisops \
  --values deploy/helm/aegisops/values.yaml \
  --values deploy/helm/aegisops/values-offline.yaml

# 4. 跑跨 helm + dockerfile 的 Python 测试
pytest deploy/tests/

# 5. 校验离线镜像清单
cat deploy/offline/images.txt
```

## 与 infra/ 的边界

- `deploy/` 不参与 `docker compose up`、不承担本地开发依赖
- `infra/` 不参与 Helm 渲染、不出现在生产镜像中
- 修改本目录下任何文件，PR 描述必须显式列出影响的镜像 / chart 版本

## 手动部署 Zabbix

在 GitHub Actions 中运行 `Deploy Component`，部署目标选择 `zabbix`。工作流复用主部署的
`aegisops-prod` 环境与腾讯云 VM 密钥，首次执行会在服务器生成
`~/workspace/aegisops/deploy/.env.zabbix`，随后启动独立的 Zabbix Compose 项目。

Web 地址为 `http://<PERF_VM_HOST>:8083`。首次登录使用 Zabbix 默认账号后应立即修改密码；
服务器安全组应仅向可信来源开放 8083，外部 Agent 需要接入时再开放 10051。
