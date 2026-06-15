# 拉取请求说明

> 本模板对应 `AGENTS.md` §17.5。**未按以下结构填写的 PR 会被打回重写。**

## 1. 背景（为什么）

<!-- 为什么需要这次变更？解决了什么用户痛点、技术债务或事故？ -->

## 2. 主要变更

<!-- 列点说明。每点以「模块/动作」开头，参考 AGENTS.md §17.3 写法。 -->

- 后端:
- 前端:
- 迁移:
- 接口:
- 安全:

## 3. 验证方式

<!-- 至少满足 AGENTS.md §16.13 四件套；Maven / 启动 / 接口 / 截图 / 录屏按需附上。 -->

- [ ] `pnpm ci:frontend` 通过（format:check / lint / typecheck / test / build）
- [ ] `pnpm ci:backend` 通过（mvn verify，跳过 Spotless 时需在 PR 描述说明原因）
- [ ] `docker compose -f infra/docker-compose.yml config` 验证
- [ ] 关键接口 curl / SQL 验证
- [ ] 截图或录屏（涉及 UI 必填）

## 4. 风险与回滚

<!-- 列出可能的影响面、依赖、数据库变更；说明如何回滚。 -->

- 风险:
- 回滚:

## 5. 关联

- Closes: #
- Refs: docs/...
- Refs-Tests: ...

## 6. Agent 自检（提交人勾选）

- [ ] AGENTS.md §16 14 条全部满足
- [ ] AGENTS.md §17 commit 规范 10 条全部勾选
- [ ] AGENTS.md §21.1 五件套全部通过
- [ ] AGENTS.md §21.3 自封模式 grep 0 命中
- [ ] AGENTS.md §21.4 raw 颜色 / space-y-\* / 自封按钮 grep 0 命中
- [ ] AGENTS.md §21.5 审计 / 凭据 mask 兜底存在
- [ ] AGENTS.md §21.7 新方法有单测、Bug 修复有复现单测

---

/assign_reviewer @aegisops/owners
