---
title: 工作记录企业级验收清单
type: research
status: draft
phase: work-record
owner: platform-team
created: 2026-07-09
updated: 2026-07-25
related:
  - docs/record/enterprise-roadmap.md
  - docs/record/api-contract.md
  - docs/record/schema-contract.md
  - docs/record/permission-contract.md
---

# 工作记录企业级验收清单

## 1. Phase 0 验收

- [ ] 冻结 `feat/record-doc-portal` 当前不可用实现。
- [ ] 明确后续只在 `web/portal` 新增工作记录功能。
- [ ] 明确 `web/console` 不再新增工作记录功能。
- [ ] 明确第一版不做微前端。
- [ ] 明确第一版不做微服务。
- [ ] 明确第一版不做完整工单流。
- [ ] `enterprise-roadmap.md` 已存在。
- [ ] `api-contract.md` 已存在。
- [ ] `schema-contract.md` 已存在。
- [ ] `permission-contract.md` 已存在。
- [ ] `acceptance-checklist.md` 已存在。
- [ ] `scripts/ci/check-record-phase0-contracts.ts` 已接入 docs CI。
- [ ] `scripts/ci/frontend.sh` 默认只跑 `web/portal`。
- [ ] `web/console` 只在 `RUN_LEGACY_CONSOLE_CI=1` 时运行。

## 2. 平台基础能力验收

- [ ] 用户管理可用。
- [ ] 角色权限可用。
- [ ] 字典类型可新增、编辑、禁用。
- [ ] 字典项可新增、编辑、禁用。
- [ ] 字典项禁用后历史记录仍能回显 label。
- [ ] 每个租户自动生成 2000–2050 年共 51 个年度工作日历及完整基础日期。
- [ ] 可下载三列 XLSX 法定节假日导入模板。
- [ ] 工作日历只允许通过 XLSX 导入法定节假日，不接受全年普通日期或调休工作日。
- [ ] XLSX 导入限制为 5 MB、1000 个非空数据行，且日期必须属于所选年度并不可重复。
- [ ] 工作日历可判断某天是否工作日。
- [ ] 工作日历可统计区间工作日数量。

## 3. 模板验收

- [ ] 可创建模板草稿。
- [ ] 可编辑模板草稿。
- [ ] 可发布模板版本。
- [ ] 已发布版本不可修改。
- [ ] 记录绑定 `templateVersionId`。
- [ ] 已被记录引用的字段编码不可修改。
- [ ] 删除字段实际为禁用。
- [ ] schema_json 与 wr_template_field 同步一致。

## 4. Schema 验收

- [ ] 只使用 `x-work-record` 嵌套扩展协议。
- [ ] fieldCode 必须匹配正则 `^[a-zA-Z][a-zA-Z0-9_]{0,63}$`。
- [ ] 保留字段不可使用。
- [ ] 支持 9 类字段。
- [ ] dict 字段必须带 dictCode。
- [ ] static select 必须带 enum。
- [ ] datetime 必须提交带 offset 的 ISO 字符串。

## 5. 记录验收

- [ ] 普通用户可新建记录。
- [ ] 普通用户可编辑自己的记录。
- [ ] 普通用户不可查看别人记录。
- [ ] 记录管理员可查看全部记录。
- [ ] 详情页按模板版本展示。
- [ ] 历史记录可按旧模板版本展示。
- [ ] 禁用字段历史值仍可展示。
- [ ] 禁用字典项历史 label 仍可展示。

## 6. 列表验收

- [ ] 分页可用。
- [ ] 标题搜索可用。
- [ ] 状态筛选可用。
- [ ] 模板筛选可用。
- [ ] 负责人筛选可用。
- [ ] 时间范围筛选可用。
- [ ] 动态字段筛选可用。
- [ ] URL search 同步可用。
- [ ] 动态列来自 listVisible 字段。

## 7. 导出验收

- [ ] 导出使用当前筛选条件。
- [ ] 导出使用当前选择列。
- [ ] 动态字段能导出真实值。
- [ ] 字典字段导出 label。
- [ ] CSV 公式注入被防护。
- [ ] 超过最大行数被拦截或明确提示。
- [ ] 导出写审计。
- [ ] exportable=false 字段不可导出。

## 8. 权限验收

- [ ] 菜单按权限显示。
- [ ] 按钮按权限显示。
- [ ] API 按权限拦截。
- [ ] 数据范围按权限控制。
- [ ] 无权限返回 403。
- [ ] 权限不足不泄露数据。

## 9. 测试验收

- [ ] 后端 service 测试通过。
- [ ] 后端 controller 测试通过。
- [ ] 后端 repository 测试通过。
- [ ] 后端 ArchUnit 测试通过。
- [ ] 前端 schema-builder 测试通过。
- [ ] 前端 dict injector 测试通过。
- [ ] 前端 runtime form 测试通过。
- [ ] 前端 records table 测试通过。
- [ ] 前端 export dialog 测试通过。
