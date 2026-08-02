# AegisOps 密钥与敏感配置管理

本文档说明本地开发、CI、部署三类场景下，如何注入数据库口令、JWT 密钥、外部 API Key 等敏感配置。

## 原则

```text
- 仓库内绝不出现真实密钥；任何提交前必须 grep 自检
- 本地开发用 .env；CI / 部署用密钥管理服务注入
- .env / .env.* 一律 .gitignore，绝不跟踪
- 日志中密钥字段必须 mask（参见 AGENTS.md §3.6）
```

## 三类场景

### 1. 本地开发（开发者本机）

```text
1. 复制 infra/env.example 为 infra/.env
2. 填写真实值
3. infra/.env 已在 .gitignore 中，git status 看不到
4. docker compose --env-file infra/.env up -d
```

注意：

- `infra/.env` 仅本机有效，不入库
- 切换分支 / 重置工作区时不要 `git clean -fd` 误删
- 离职 / 移交机器前请先 rotate 全部 key

### 2. 持续集成（GitHub Actions）

```text
- 在 GitHub 仓库 Settings → Secrets and variables → Actions 配置
- 命名约定：与小写环境变量名一致，例如 AIOPS_DB_PASSWORD
- 工作流中通过 ${{ secrets.AIOPS_DB_PASSWORD }} 引用
- 严禁把 secrets 写到 GITHUB_ENV / 工作流日志 / artifact 中
```

新增密钥流程：

```text
1. Owner 在 GitHub Secrets 添加键值
2. 更新 infra/env.example 中对应行的注释（说明用途），不放真实值
3. 更新本文件 1.本地开发 段落说明用法
4. 在 PR 描述中列出新增的 secret 名称（不放值）
```

### 3. 生产 / 预发部署

```text
- Helm values 走 values.yaml 默认值（占位）+ values-private.yaml（git 忽略）+ Secret 对象
- 推荐使用外部密钥管理：阿里云 KMS / HashiCorp Vault / AWS Secrets Manager
- 离线部署场景（deploy/offline/）需要先在镜像打包阶段把密钥写入 initContainer / Sidecar
- 任何镜像 / chart 包不得携带明文密钥
```

Helm 引用方式：

```yaml
env:
  - name: AIOPS_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: aegisops-secrets
        key: db-password
```

## 自检清单（提交前）

```text
[ ] git status 无 .env 改动
[ ] git ls-files | grep -E '\.env$' 为空
[ ] git diff --staged 中无 key 字面量（如 sk-、AIza、AKIA、ghp_ 等前缀）
[ ] infra/env.example 中所有值均为 REPLACE_ME / 占位字符串
[ ] 日志代码中 DB 密码 / JWT / API Key 必须走 Logback MaskingPatternConverter
```

## 已声明的密钥命名

```text
AIOPS_DB_URL          # PostgreSQL JDBC URL
AIOPS_DB_USERNAME     # DB 用户名
AIOPS_DB_PASSWORD     # DB 密码
AIOPS_JWT_SECRET      # JWT 签名密钥，至少 32 字节
AIOPS_DEEPSEEK_API_KEY # DeepSeek LLM API Key
AIOPS_SERVER_PORT     # aegisops-app 公共 API 监听端口
AIOPS_RUNNER_PORT     # aiops-runner 监听端口
```

新增密钥时：

1. 在 env.example 加占位行（`AIOPS_NEW_KEY=REPLACE_ME`）
2. 在本文档「已声明的密钥命名」追加一行注释
3. 在 ADR 目录新增一份 `docs/adr/XXXX-key-naming.md` 说明引入背景
