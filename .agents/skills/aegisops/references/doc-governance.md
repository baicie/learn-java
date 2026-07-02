# 文档治理

## 规则

所有项目文档必须放在 `docs/` 下。

不要在仓库根目录随意创建 markdown 文件, 以下白名单除外:

```txt
- README.md
- AGENTS.md
- CHANGELOG.md
- LICENSE
```

## 文档位置

稳定架构文档:

```txt
- docs/architecture/
```

架构决策记录:

```txt
- docs/adr/
```

Phase 计划与验收:

```txt
- docs/phases/<phase>/
```

特性设计:

```txt
- docs/designs/<phase>/
```

评审:

```txt
- docs/reviews/<phase>/
```

修复计划:

```txt
- docs/fixes/<phase>/
```

API 文档:

```txt
- docs/api/
```

数据库文档:

```txt
- docs/database/
```

集成文档:

```txt
- docs/integrations/
```

AI / RCA 文档:

```txt
- docs/ai/
```

运维文档:

```txt
- docs/operations/
```

Runbook 文档:

```txt
- docs/runbooks/
```

调研文档:

```txt
- docs/research/
```

## 必备 frontmatter

`docs/` 下每篇文档都必须有:

```yaml
---
title: 示例标题
type: design
status: draft
phase: phase-0
owner: ai
created: 2026-06-12
updated: 2026-06-12
related: []
---
```

## status 取值

允许的状态值:

```txt
- draft        进行中
- review       评审中
- accepted     已批准并稳定
- deprecated   已被新决策替代
```

## type 取值

允许的 type 值:

```txt
- architecture     稳定架构文档
- adr              架构决策记录
- phase            Phase 计划与验收
- design           特性设计文档
- review           设计或代码评审
- fix              Bug 修复与维修计划
- api              API 规范
- database         Schema 与 migration 文档
- integration      外部系统集成
- ai               AI、RCA、Prompt 文档
- operation        部署、运维、排障
- runbook          内置产品 Runbook
- research         产品与技术调研
```

## 命名

统一小写文件名。

使用 kebab-case (短横线命名)。

流程文档需带日期前缀:

```txt
docs/designs/phase-0/2026-06-12-project-foundation.md
docs/reviews/phase-1/2026-06-12-zabbix-adapter-review.md
docs/fixes/phase-1/2026-06-12-zabbix-auth-failed.md
```

ADR 使用编号文件名:

```txt
docs/adr/0001-use-java-spring-boot.md
docs/adr/0002-use-modular-monolith.md
```

## 脚本用法

初始化目录:

```bash
npx tsx scripts/docs.ts init
```

新建文档:

```bash
npx tsx scripts/docs.ts new design project-foundation --title "项目基础设计" --phase phase-0
npx tsx scripts/docs.ts new adr use-java-spring-boot --title "采用 Java Spring Boot"
npx tsx scripts/docs.ts new fix zabbix-auth-failed --title "修复 Zabbix 鉴权失败" --phase phase-1
npx tsx scripts/docs.ts new review phase-0-review --title "Phase 0 实施评审" --phase phase-0
npx tsx scripts/docs.ts new phase phase-1 --title "Phase 1 Zabbix 数据接入" --phase phase-1
```

校验所有文档的 frontmatter 与命名:

```bash
npx tsx scripts/docs.ts check
```

重新生成索引:

```bash
npx tsx scripts/docs.ts index
```

## Agent 规则

在用户要求新建 design、review、fix、ADR 或 phase 文档时, 优先使用 docs 脚本。

不要在目录约定之外的路径创建文档。

不要手工编辑 `docs/INDEX.md`, 该文件由 `docs:index` 生成。

已接受的 ADR 不得修改; 若决策变更, 新建一份 ADR。

按 Phase 分目录的文档 (designs, reviews, fixes) 必须落到对应 Phase 的子目录下。

稳定文档 (architecture, api, database, integrations, ai, operations, runbooks, research) 直接放在对应目录顶层。
