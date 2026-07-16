---
title: 国内网络自建 GitHub Actions Runner 运维指南
type: operation
status: review
phase: phase-8
owner: ai
created: 2026-07-16
updated: 2026-07-16
related:
  - .github/workflows/ci.yml
  - .github/workflows/release-verify.yml
  - scripts/ci/self-hosted-runner-preflight.sh
---

# 国内网络自建 GitHub Actions Runner 运维指南

## 目标方案

使用一台专用 Linux x64 主机承载私有仓库 CI，通过标签
`self-hosted`、`linux`、`x64`、`aegisops-cn` 路由任务。大文件下载不再依赖每次任务临时完成：工具链预装，Maven、pnpm、pip、Playwright 和 BuildKit 状态保存在 Runner 主机。

推荐配置为 Ubuntu 22.04/24.04、8 核、16 GiB 内存、200 GiB SSD。Runner 必须使用独立的非登录系统用户，不与生产服务、数据库或 AegisOps 业务 Runner 共机。

## 安全前提

仓库当前是私有仓库，但自建 Runner 仍会直接执行 PR 中的代码：

1. 在组织设置创建 `aegisops-cn` Runner group，仅允许 `baicie/ai-ops` 使用。
2. 禁止来自 Fork 的 PR 使用该 Runner；如以后开放外部贡献，改用一次性 `--ephemeral` Runner。
3. `aegisops-prod` Environment 保留人工审批，生产 Secrets 只在部署 Job 中可见。
4. Runner 主机不保存生产 SSH key、Docker Hub token 或项目 `.env`；持久目录只放依赖与构建缓存。
5. Docker group 等价于宿主机高权限，只允许受信任维护者修改可触发 workflow 的代码。

GitHub 官方建议使用 Runner group 限制仓库访问，并警告不要让不受信任的公共 Fork 在持久自建 Runner 上执行代码：<https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access>。

## 注册 Runner

在 GitHub 仓库的 `Settings → Actions → Runners → New self-hosted runner` 中选择 Linux x64，使用页面实时生成的下载、校验和注册命令。注册时追加自定义标签：

```bash
./config.sh \
  --url https://github.com/baicie/ai-ops \
  --token '<页面生成的一次性注册令牌>' \
  --labels aegisops-cn \
  --unattended
sudo ./svc.sh install
sudo ./svc.sh start
```

注册令牌不要写入仓库、Shell 历史、镜像或自动化日志。Runner 安装包可在网络良好的可信主机下载，校验 GitHub 页面提供的 SHA-256 后通过内网传入；注册和接收任务仍必须连接 GitHub。

GitHub 的 Runner 注册、标签与路由规则见：

- <https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners>
- <https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/use-in-a-workflow>

## 预装工具链

主机必须预装并固定以下版本：

| 工具       | 版本/要求                                     |
| ---------- | --------------------------------------------- |
| Java       | Temurin 21                                    |
| Maven      | 3.9.x                                         |
| Node.js    | 22.x                                          |
| pnpm       | 10.33.4                                       |
| Python     | 3.12.x                                        |
| Docker     | Engine + Compose v2 + Buildx，服务必须已启动  |
| PostgreSQL | `psql` 客户端                                 |
| ShellCheck | 当前发行版                                    |
| Playwright | 与 Portal lockfile 对应的 Chromium 及系统依赖 |

首次准备 Playwright 时，在仓库检出目录执行一次：

```bash
export PLAYWRIGHT_BROWSERS_PATH="$HOME/.cache/aegisops-ci/playwright"
pnpm install --frozen-lockfile --prefer-offline \
  --store-dir "$HOME/.cache/aegisops-ci/pnpm"
pnpm -C web/portal exec playwright install --with-deps chromium
```

每个 Job 会运行 `scripts/ci/self-hosted-runner-preflight.sh`。版本或能力不匹配时立即失败，不在任务中临时安装或静默升级。

## 国内下载加速顺序

### 1. 首选内网代理仓库

推荐在国内部署 Nexus、Artifactory 或同类只读代理，统一代理 Maven Central、npm 与 PyPI。代理应启用上游校验、访问控制、审计和备份。不要把带用户名密码的代理 URL 写入 GitHub Variables。

- Maven：在 Runner 用户的 `~/.m2/settings.xml` 配置内部 mirror。
- npm/pnpm：仓库变量 `AIOPS_NPM_REGISTRY` 配置为内网 npm 代理 URL。
- pip：仓库变量 `AIOPS_PIP_INDEX_URL` 配置为内网 PyPI 代理 URL。

未设置变量时使用主机现有配置和官方源。公共国内镜像只适合作为临时后备，使用前需接受其同步延迟和供应链风险。

### 2. Docker 镜像

执行仓库已有脚本配置腾讯云或企业 Docker mirror：

```bash
DOCKER_MIRROR_URL=https://mirror.ccs.tencentyun.com \
  bash deploy/scripts/configure-docker-mirror.sh
```

定期预拉取 PostgreSQL、MinIO、Flyway 和项目 Dockerfile 使用的基础镜像。Release workflow 直接复用主机预装的 Buildx 与 Docker daemon 持久缓存，不再临时下载 Buildx，也不把构建缓存上传到 GitHub Cache。

### 3. GitHub 自身链路

Runner 仍必须通过 443 访问 GitHub。优先使用稳定的企业出口代理，并在 Runner 服务启动前设置小写的 `https_proxy`、`http_proxy`、`no_proxy`。官方支持在 Runner 安装目录的主机级 `.env` 配置代理；该文件只能存在于主机，不能提交到本仓库。不要关闭 TLS 校验。

网络至少放行 `github.com`、`api.github.com`、`*.actions.githubusercontent.com`、`codeload.github.com`、`objects.githubusercontent.com`、`github-releases.githubusercontent.com`、`results-receiver.actions.githubusercontent.com` 和相关 CNAME。完整清单以 GitHub 官方文档为准：

- <https://docs.github.com/en/actions/reference/runners/self-hosted-runners#communication>
- <https://docs.github.com/en/actions/how-tos/manage-runners/use-proxy-servers>

## 缓存与清理

预检脚本默认使用 `$HOME/.cache/aegisops-ci`：

```text
maven/       Maven 本地仓库
pnpm/        pnpm store
pip/         pip wheel/download cache
playwright/  Chromium
```

BuildKit state 由持久自建 Runner 保存。每周监控磁盘，超过 80% 时先用 Maven/pnpm/pip 自带清理命令和 `docker buildx prune --filter until=168h` 清理旧缓存。不得直接清理正在运行 Job 的目录。

GitHub 文档说明，自建 Runner 使用 `actions/cache` 时缓存仍存储在 GitHub 云端，因此国内场景不把它作为主要加速手段：<https://docs.github.com/en/actions/concepts/workflows-and-actions/dependency-caching>。

## 验证与回滚

主机上线后先执行：

```bash
bash scripts/ci/self-hosted-runner-preflight.sh \
  java node python docker postgres shellcheck
bash scripts/ci/test-self-hosted-runner-contract.sh
```

然后手工触发 CI，确认 GitHub 页面显示 Runner 标签 `aegisops-cn` 且四个主任务成功。若 Runner 离线，匹配该标签的任务会排队，超过 24 小时失败。

回滚时把 workflow 的 `runs-on` 恢复为 `ubuntu-latest`，恢复 `setup-java/setup-node/setup-python` 和 Playwright 安装步骤；停止 Runner 服务不会自动把任务转移到 GitHub 托管 Runner。
