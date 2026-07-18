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
