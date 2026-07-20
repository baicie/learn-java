# deploy — 生产 / 预发部署

本目录承载 AegisOps 在 Kubernetes / 离线环境下的部署产物。

## 边界

```text
infra/    ── docker-compose + env.example（本地开发）
deploy/   ── Helm chart + Dockerfile + 离线包（生产 / 预发部署）

两个目录职责严格分离，绝不互相替代：
- 本地开发用 infra/docker-compose.yml
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
├─ docker-compose.app.yml           # 腾讯云 VM 部署用的 compose（postgres + server + agent + worker + runner）
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

## 运行时拓扑

```text
生产 VM（部署单元）：
  postgres  ── 5432  ──┐
                        ├── aiops-server   (8080, 控制面, Spring Boot)
                        ├── aiops-worker   (8081, 异步消费 + RCA/Incident 聚合)
                        └── aiops-runner   (8092, 隔离执行 ansible / webhook)

  aiops-agent  (9008, Python LangGraph) ── 通过 internal token 调 aiops-server internal API
```

说明：

- worker / runner 跟 server 共享同一 Postgres（独立 schema 与表，互不耦合）
- runner 镜像额外装了 `ansible-playbook` / `sshpass` / `openssh-client` 与 `tini`
- worker / runner 不依赖 agent（agent 走 canonical workflow 只查 server，不直接调 runner）

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
- 推荐外部密钥源：阿里云 KMS / HashiCorp Vault / AWS Secrets Manager
- CI 端：在 GitHub Secrets 配置对应键，由部署脚本注入到 Secret 对象
- 严禁任何明文密钥出现在 `values.yaml`、`templates/*.yaml`、`Dockerfile` 中

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
