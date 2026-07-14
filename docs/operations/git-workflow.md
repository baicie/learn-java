---
title: Git 工作流
type: operation
status: accepted
phase: phase-8
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - .github/workflows/ci.yml
  - .github/workflows/release-verify.yml
  - .github/workflows/deploy.yml
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
- `Ops Scripts`：仅在 `ops/**` 或 `infra/**` 变化时运行。
- `Work Record E2E`：需要完整 E2E 时给 PR 添加 `e2e` 标签。
- `Release Verify`：`mvp` 的发布相关变化执行容器构建与运行时冒烟。
- `Deploy`：仅在 `mvp` 的 `Release Verify` 成功后自动部署。

`main` 快照提升单独通过 `mvp -> main` PR 完成，不在功能 PR 中混做。

## 分支保护

仓库管理员在 Actions 中手动运行 `Branch Protection`，默认保护 `main,mvp`，要求：

- `CI Summary`
- `Block .env and key literals`
- 1 个 Code Owner 审批
- 线性历史、对话全部解决、禁止强推与删除

该工作流需要仓库 Secret `BRANCH_PROTECTION_TOKEN`，令牌必须具备仓库管理权限。
