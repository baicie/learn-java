---
title: 工作记录 Phase 00 产品边界与总体契约
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/index.md
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/portal/SKILL.md
---

# Phase 00：产品边界与总体契约

## 1. 设计结论

工作记录模块的最佳第一版不是“低代码平台”，也不是“工单系统”，而是：

```text
面向运维团队的可配置记录台账
```

它解决的是这类问题：

```text
运维团队每天有大量非 Incident 的过程性记录：
  - 日常巡检
  - 值班交接
  - 发布记录
  - 变更记录
  - 故障处置摘要
  - 临时处理记录

这些记录格式会随团队变化，但不应该每次都改代码。
```

因此第一版核心价值是：

```text
管理员配置模板
用户按模板填写
列表可筛选
历史可追溯
导出受控
权限和审计可信
```

## 2. 最佳方案

推荐方案：

```text
后端：
  PostgreSQL + wr_template + wr_template_field + wr_record
  Formily-compatible schema_json 作为渲染协议
  wr_template_field 作为查询、校验、导出、列表列的字段索引

前端：
  web/portal
  shadcn-admin 原生布局
  TanStack Router + TanStack Query + TanStack Table
  portal-native 模板设计器
  Formily runtime 渲染动态表单

设计器：
  第一版不强塞完整 Designable AntD UI
  使用 shadcn/ui 构建字段面板、属性面板、预览区
  输出标准 schema_json
  后续若确需拖拽画布，再通过 ADR 引入 Designable 作为高级设计模式
```

为什么不直接把 Designable 当第一版主路径：

```text
1. Designable 生态偏 Ant Design，与 portal 的 Radix / shadcn 风格不一致。
2. 工作记录第一版需要的是“字段配置器”，不是页面级低代码。
3. 字段数量通常有限，列表式字段设计器比复杂画布更高效。
4. 运行态一致性比设计态炫技更重要。
5. 最重要的复杂度在后端校验、权限、历史展示、导出，而不是拖拽本身。
```

如果用户强需求“拖拽式设计器”，推荐做成二阶段：

```text
阶段 A：portal-native 字段设计器，稳定协议和运行态。
阶段 B：Designable 高级模式，只替换设计器 UI，不改后端 schema 契约。
```

## 3. 系统边界

### 3.1 属于工作记录的能力

```text
平台字典
模板管理
字段管理
动态表单填写
记录详情
记录列表
受控筛选
受控导出
权限控制
审计日志
```

### 3.2 不属于第一版的能力

```text
流程审批
SLA
评论
附件
Excel 导入
复杂统计大屏
字段级权限
复杂模板版本树
子表单
公式字段
远程接口字段
告警联动
巡检联动
AI 总结
完整 LowCodeEngine
微前端
微服务
```

这些不是永远不做，而是不进入第一版主路径。

## 4. 用户与场景

### 4.1 系统管理员

职责：

```text
配置租户、用户、角色、权限。
维护系统级字典。
查看审计日志。
```

关注点：

```text
安全
可追溯
权限边界清晰
默认配置可用
```

### 4.2 记录管理员

职责：

```text
维护工作记录模板。
维护业务字典。
查看团队全部记录。
导出记录。
```

关注点：

```text
模板易配置
字段不会误删历史
筛选和导出好用
字段变更可追踪
```

### 4.3 普通记录用户

职责：

```text
填写记录。
查看自己创建或负责的记录。
编辑自己有权限编辑的记录。
```

关注点：

```text
填写快
字段含义清楚
必填校验明确
历史记录容易找到
```

## 5. 领域对象

### 5.1 DictType

平台字典类型。

```text
dict_code 是稳定业务编码，例如 record_type。
dict_name 是展示名称，例如 记录类型。
system_builtin 表示系统内置，不能被禁用。
enabled=false 表示软禁用。
```

### 5.2 DictItem

字典项。

```text
item_value 是记录存储值。
item_label 是展示文案。
历史记录保存 value，不保存 label。
禁用项仍需支持历史展示。
```

### 5.3 WorkRecordTemplate

工作记录模板。

```text
schema_json：
  表单渲染协议
  设计器还原依据

wr_template_field：
  字段索引
  后端校验依据
  列表列依据
  筛选白名单
  导出列依据
```

### 5.4 WorkRecordField

模板字段索引。

```text
field_code：
  稳定字段编码
  创建后原则上不可修改

field_type：
  值类型约束
  创建后原则上不可修改

option_source：
  static 或 dict

enabled：
  字段软删除
```

### 5.5 WorkRecord

工作记录实例。

```text
内置字段放主表：
  title
  status
  owner_id
  creator_id
  record_time

动态字段放 custom_data_json。
```

## 6. 总体架构

```text
web/portal
  ↓ HTTP /api
apps/aiops-server
  ↓ Spring Bean scan
modules/aiops-platform/dictionary
modules/aiops-work-record
modules/aiops-audit
modules/aiops-security
  ↓ JDBC / PostgreSQL
platform_dict_type
platform_dict_item
wr_template
wr_template_field
wr_record
audit_log
```

边界原则：

```text
1. 字典属于 aiops-platform，不属于 work-record 私有能力。
2. 工作记录属于 aiops-work-record，不依赖 incident、alert、inspection。
3. 用户和角色复用 aiops-user / aiops-security。
4. 所有租户维度查询必须带 tenant_id。
5. 所有配置变更、删除、导出必须写审计。
```

## 7. 前端总体架构

```text
web/portal/src/
├─ i18n/
├─ features/
│  ├─ dictionaries/
│  ├─ roles/
│  └─ work-records/
└─ routes/_authenticated/
   ├─ platform/
   └─ work-records/
```

状态分层：

```text
URL search：
  列表分页
  状态筛选
  模板筛选
  字段筛选

TanStack Query：
  字典
  模板
  字段
  记录

React local state：
  弹窗开关
  表单临时值
  设计器选中字段

Zustand：
  只保留 auth/sidebar/theme，不存业务数据。
```

## 8. 路由

```text
/work-records
  记录列表

/work-records/new
  新建记录

/work-records/$recordId
  记录详情

/work-records/$recordId/edit
  编辑记录

/work-records/designer
  模板设计器

/platform/dictionaries
  字典管理

/platform/roles
  角色权限
```

## 9. 权限模型

第一版权限：

```text
platform:dict:read
platform:dict:write

work-record:template:read
work-record:template:write

work-record:record:read:self
work-record:record:read:all
work-record:record:write
work-record:record:delete
work-record:export

role:read
role:write
audit:read
```

普通用户：

```text
platform:dict:read
work-record:template:read
work-record:record:read:self
work-record:record:write
```

记录管理员：

```text
普通用户权限
platform:dict:write
work-record:template:write
work-record:record:read:all
work-record:record:delete
work-record:export
```

系统管理员：

```text
所有权限
```

## 10. 数据一致性规则

### 10.1 字典

```text
新增记录时：
  只允许写入 enabled=true 的字典项 value。

展示历史记录时：
  允许读取 enabled=false 的字典项 label。

禁用字典项：
  不影响历史记录显示。
```

### 10.2 模板字段

```text
字段删除：
  enabled=false，不物理删除。

字段编码：
  创建后不可普通编辑。

字段类型：
  创建后不可普通编辑。

字段必填：
  仅影响未来保存，不应破坏历史展示。
```

### 10.3 记录

```text
记录创建：
  tenant_id、creator_id、created_at 由服务端生成。

记录更新：
  不能更新 tenant_id、creator_id、created_at。

记录删除：
  deleted_at 软删除。
```

## 11. API 风格

所有 API 返回：

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": null,
  "timestamp": "2026-07-08T10:00:00+08:00"
}
```

分页返回：

```json
{
  "total": 100,
  "page": 1,
  "size": 20,
  "items": []
}
```

错误语义：

```text
400：字段校验失败
401：未登录
403：权限不足
404：资源不存在或不可见
409：编码冲突、状态冲突
500：系统错误
```

## 12. 测试策略

后端：

```text
Service 单元测试：
  字典 CRUD
  字典禁用历史展示
  字段 required
  字段类型校验
  select/multi_select 选项校验
  self/all 权限
  导出上限
  审计写入

Repository 集成测试：
  tenant_id 查询隔离
  JSONB 筛选参数绑定
  分页排序

ArchUnit：
  Controller 不直连 JDBC
  work-record 不依赖 alert/incident/inspection repository
```

前端：

```text
纯函数测试：
  i18n key
  schema 字段提取
  字典注入
  字段值格式化

组件测试：
  字典页
  模板设计器
  动态表单
  记录列表 URL search

构建测试：
  lint
  typecheck
  test
  build
```

## 13. Phase 切分

```text
Phase 01：portal i18n、菜单与路由壳
Phase 02：平台字典完整管理
Phase 03：模板与字段后端契约
Phase 04：portal-native 模板设计器
Phase 05：动态表单运行态
Phase 06：列表、动态筛选与导出
Phase 07：权限、审计、边界与交付收口
```

## 14. 验收总标准

第一版完成时必须满足：

```text
1. 管理员能创建字典和字典项。
2. 管理员能创建模板和字段。
3. 用户能按模板填写记录。
4. 记录详情能按模板顺序展示字段。
5. 禁用字段和禁用字典项不影响历史展示。
6. 普通用户只能看自己的记录。
7. 管理员能看租户内全部记录。
8. 列表支持分页、状态、模板、白名单动态字段筛选。
9. 导出受权限控制、有行数上限、不通过 URL 暴露 token。
10. 所有配置变更、删除、导出都有审计。
11. portal lint/test/build 可通过。
12. backend test 可通过。
```
