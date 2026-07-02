# Phase 清单

在启动、评审或收尾一个 Phase 时使用本清单。

## Phase 启动清单

开始新 Phase 之前:

```txt
- [ ] 前一个 Phase 已通过验收 (所有验收标准都已满足)
- [ ] 在 docs/phases/<phase>/ 下存在 Phase 设计文档
- [ ] 该 Phase 的所有设计文档已落地在 docs/designs/<phase>/
- [ ] 新增架构决策已写入 ADR
- [ ] 没有违反既有 ADR
- [ ] 已遵守 docs/architecture/ 中的模块边界定义
```

## Phase 进行中清单

Phase 推进过程中:

```txt
- [ ] 每个功能先有设计文档再实现
- [ ] 所有数据库变更都有 Flyway migration
- [ ] 外部系统只通过 Adapter 访问
- [ ] 每条查询都守护租户隔离
- [ ] 敏感操作都生成审计日志
- [ ] 没有实现任何未来 Phase 的功能
- [ ] 没有过早拆微服务
- [ ] 核心领域逻辑有测试覆盖
- [ ] 每篇新文档都含合法 frontmatter
- [ ] 文档都已放入正确目录
```

## Phase 评审清单

接受 Phase 之前:

```txt
- [ ] 所有验收标准都已验证
- [ ] 所有计划交付物都已实现
- [ ] 设计文档已更新为反映实现现状
- [ ] OpenAPI 已反映 API 变更
- [ ] README / AGENTS.md / SKILL.md 已同步新约定
- [ ] 已知 P0 / P1 问题均已关闭
- [ ] 代码可编译、测试通过
- [ ] MVP 演示路径端到端依然可用
```

## Phase 收尾清单

收尾一个 Phase 时:

```txt
- [ ] Phase 验收文档已更新到最终状态
- [ ] Phase 状态置为 accepted
- [ ] 下一阶段的设计文档已创建或启动
- [ ] 本阶段的进行中设计 / 评审 / 修复要么完成要么显式延期
- [ ] docs/INDEX.md 已重新生成
```

## 每 Phase 关卡

每个 Phase 必须先通过 MVP 演示关卡再收尾:

| Phase   | 关卡                                               |
| ------- | -------------------------------------------------- |
| Phase 0 | 可登录、空 Dashboard 可渲染、API 文档可访问        |
| Phase 1 | Zabbix 数据源同步主机与告警, AlertEvent 已创建     |
| Phase 2 | 告警聚合成 Incident, Incident 详情展示时间线       |
| Phase 3 | Incident 详情展示指标与 RCA 证据链                 |
| Phase 4 | AI 诊断产出带证据的结构化输出                      |
| Phase 5 | 审批通过后 Runbook 通过 Ansible 执行, 日志流式回传 |
| Phase 6 | 关闭 Incident 时生成 Postmortem 草稿               |
