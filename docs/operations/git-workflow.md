---
title: Git 工作流
type: operation
status: accepted
phase: phase-8
owner: ai
created: 2026-07-14
updated: 2026-08-03
related:
  - .github/workflows/ci.yml
  - .github/workflows/ops-scripts.yml
  - .github/workflows/release-verify.yml
  - .github/workflows/deploy-component.yml
---

# Git 工作流

当前 MVP 阶段使用一个开发主干：`mvp`。`main` 只保留稳定快照，不承接日常功能分支。

## 日常流程

1. 从最新 `mvp` 创建短分支：`feat/*`、`fix/*`、`refactor/*`、`docs/*` 或 `chore/*`。
2. 每个分支只解决一个问题，目标在 1–3 天内通过 PR 合回 `mvp`。
3. 提交信息使用 Conventional Commits；中文说明变更意图。
4. PR 必须通过 `CI Summary` 和 `Block .env and key literals`，解决评审意见后再合并。
5. 默认使用 squash merge，合并后删除分支。

```bash
git switch mvp
git pull --ff-only
git switch -c feat/<short-name>

pnpm ci
git push -u origin HEAD
```

禁止直接推送、强推或删除 `mvp`、`main`。不要恢复长期 `develop` 分支；需要并行开发时创建独立短分支或 worktree。

## CI 与发布边界

- `CI`：所有指向 `mvp` 或 `main` 的 PR 都运行文档、后端、前端和 Agent 验证。
- `Secrets Guard`：所有 PR 与主干 push 都扫描被跟踪的环境文件和密钥字面量。
- `Ops Scripts`：在运维脚本、基础设施、Flyway migration 或 Docker Runner 准备脚本变化时串行运行 Shell 与 PostgreSQL smoke；已有 `shellcheck` / `psql` 时不重复执行 apt 下载。
- `Work Record E2E`：需要完整 E2E 时给 PR 添加 `e2e` 标签。
- `Release Verify`：所有 PR 只运行 Release preflight；`mvp` 的 CI 成功后或手动触发时，构建三个 commit SHA 镜像并完成 Compose smoke，通过后推送同一批镜像并部署到腾讯云 VM。镜像全程只构建一次。

PR 合并门禁保持稳定，不对 required workflow 使用路径级跳过：

- `CI Summary`：汇总 Docs、Backend、Frontend 与 AI Agent，任一失败即阻断。
- `Block .env and key literals`：扫描被跟踪的环境文件与常见密钥字面量。

容器构建、Compose runtime smoke、Work Record E2E 和生产部署不属于普通 PR 的必跑路径。

## GitHub 托管 Runner 资源纪律

工作流统一使用 GitHub 托管 Runner。每个 job 的文件系统和 Docker daemon 相互隔离，因此不得依赖跨 job 的本地缓存或本地镜像。

- CI 的 Docs、AI Agent、Frontend、Backend 保留独立检查名。
- Release 在 CI 成功后启动，镜像构建、smoke、推送和生产部署串行执行。
- Release 的生产部署 job 与手工 `Deploy Component` 共用 `aegisops-prod-deploy` 并发组；任何
  会覆盖生产 VM 部署目录的 workflow 必须使用同一串行锁。
- 生产 VM 只接收 smoke 后推送并经 registry 核对的三个 `repository@sha256:<digest>`；commit SHA
  标签只用于定位发布，不作为不可变部署凭据。
- 覆盖率产物只在失败时上传，成功构建不传输报告目录。
- Work Record E2E 的 Playwright 报告和服务日志也只在失败时上传。

手工生产发布同样受以下门禁约束：

- 只能从 `refs/heads/mvp` 触发，目标 SHA 必须存在成功的 `CI` run。
- 描述符只上传到 run 级 staging 目录；远端 manifest、网络与 Compose 校验通过后才能备份并提升
  active 文件，禁止 SCP 直接覆盖 active 目录。
- 携带生产 SSH 凭据的第三方 Action 必须固定完整 commit SHA，并校验离线核对的服务器 ED25519
  指纹；host key 轮换必须通过 PR 同步所有生产 workflow。
- `aegisops-prod` Environment 必须配置 required reviewers 与只允许 `mvp` 的 deployment branch
  policy；生产 VM、DockerHub 与模型凭据应迁移为该 Environment 的 secrets。workflow 中的分支
  和 CI 检查不能替代仓库管理员配置。
- 不得通过重跑旧 workflow、临时改 ref 或复制未验证镜像绕过门禁。紧急发布仍需记录目标 SHA、
  审批人、备份位置、验收证据和明确回滚入口。

`main` 快照提升单独通过 `mvp -> main` PR 完成，不在功能 PR 中混做。

## 分支保护

仓库管理员在 Actions 中手动运行 `Branch Protection`，默认保护 `main,mvp`，要求：

- `CI Summary`
- `Block .env and key literals`
- 1 个 Code Owner 审批
- 线性历史、对话全部解决、禁止强推与删除

该工作流需要仓库 Secret `BRANCH_PROTECTION_TOKEN`，令牌必须具备仓库管理权限。
