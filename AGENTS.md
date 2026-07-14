# AGENTS.md

本文件是 AegisOps / FaultLens 项目的 Agent 入口说明。项目规范以 Skill 为准；本文件只保留启动流程、优先级和本地执行提醒，避免与 Skill 维护两套规则。

## 1. 单一真相源

实施、评审、Phase 路线图、架构边界、代码质量、安全、多租户、文档治理等规则按以下顺序生效：

```text
1. .agents/skills/aegisops/SKILL.md
2. .agents/skills/aegisops/references/architecture-boundaries.md
3. .agents/skills/aegisops/references/automation-safety.md
4. .agents/skills/aegisops/references/doc-governance.md
5. .agents/skills/aegisops/references/phase-checklist.md
6. AGENTS.md（本文件，仅作入口与执行提醒）
```

如本文件与 Skill 或 references 冲突，以 Skill 和 references 为准，并优先精简或删除本文件中的重复内容。

## 2. Agent 启动流程

仓库仅启用两个项目 Skill：

```text
.agents/skills/aegisops  # 全仓架构、后端、安全、Phase 与文档治理
.agents/skills/portal    # web/portal 前端约定
```

其余通用 Skill 保留在 `.agents/skills/`，仅在任务匹配或被显式点名时使用，不改变上述项目规则优先级。

每次处理本仓库任务时：

```text
1. 先阅读 .agents/skills/aegisops/SKILL.md
2. 按任务类型阅读对应 references/*.md
3. 明确当前 Phase，遵守 SKILL.md §14 与 §21
4. 不越级实现后续阶段功能
5. 修改外部接口时同步更新 OpenAPI 与 docs/api/*
6. 修改数据库结构时新增 Flyway migration，命名按 SKILL.md §17
7. 修改安全、权限、审计、自动化执行路径时同步补测试与审计说明
8. 写前端 UI 时遵守 SKILL.md §4.1，并优先使用 shadcn/ui 组件
9. 默认使用中文与用户沟通、写文档、写提交说明和 PR 描述
```

## 3. 当前最高优先级

任何实现和评审都优先服务于 MVP 闭环：

```text
Zabbix 告警
  -> AlertEvent 归一
  -> Incident 聚合
  -> 证据链 / RCA
  -> AI 诊断
  -> Runbook 推荐
  -> 人工审批后的 runner 执行
  -> 审计与复盘
  -> 历史知识复用
```

永远优先保证：

```text
Incident 模型
证据链
自动化安全边界
审计
多租户隔离
MVP 闭环
```

不要优先追求：

```text
酷炫大屏
复杂 AI Agent
过早微服务
复杂拓扑图
完全自动修复
全量采集平台
```

## 4. 本地验证入口

提交或交付前按影响面运行对应验证。完整本地验证优先使用：

```bash
bash scripts/ci/verify-local.sh
```

常用分项：

```bash
bash scripts/ci/docs.sh
bash scripts/ci/backend.sh
bash scripts/ci/frontend.sh
bash scripts/ci/agent.sh
```

如果本机默认 JDK 低于项目要求，显式指定 Java 21+：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) bash scripts/ci/backend.sh
```

当前仓库也可在只有更高版本 JDK 的环境下运行 Maven，但若出现工具链兼容告警，应优先修构建配置或在结果中说明。

## 5. 文档与提交提醒

- 新增或修改项目文档时，按 `SKILL.md §25` 与 `references/doc-governance.md` 放入 `docs/` 对应目录。
- 不手工维护 `docs/INDEX.md`；需要时运行文档脚本重新生成。
- 纯根目录文档仅允许 `README.md`、`AGENTS.md`、`CHANGELOG.md`、`LICENSE`。
- Commit、PR、PR-Tier、风险分级与评审要求以后统一在 Skill 或 references 中维护；本文件不再复制完整规范。

```text
本项目真正有价值的不是“接入了多少数据源”，而是：

能否把一次线上故障从发现、定位、处置到复盘真正串起来。
```
