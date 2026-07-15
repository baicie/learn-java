---
title: 工作记录阶段对话记录
type: research
status: draft
created: 2026-07-09
updated: 2026-07-14
---

# 企业级重做路线图

## Phase 0：冻结现状，重新定边界

目标：停止在当前不可用实现上继续堆功能。

做什么：

```text
1. 冻结 feat/record-doc-portal 当前实现
2. 保留现有代码作为参考，不直接继续扩展
3. 明确 web/portal 是唯一前端入口
4. 明确 web/console 不再新增工作记录功能
5. 明确第一版不做微前端、不做微服务、不做完整工单流
6. 重新整理接口契约、权限码、表结构、前端路由
7. 建立 record 模块验收清单
```

交付物：

```text
1. docs/record/enterprise-roadmap.md
2. docs/record/api-contract.md
3. docs/record/schema-contract.md
4. docs/record/permission-contract.md
5. docs/record/acceptance-checklist.md
```

---

1. 冻结 feat/record-doc-portal 当前实现
2. 保留现有代码作为参考，不直接继续扩展
3. 明确 web/portal 是唯一前端入口
4. 明确 web/console 不再新增工作记录功能
5. 明确第一版不做微前端、不做微服务、不做完整工单流
6. 重新整理接口契约、权限码、表结构、前端路由
7. 建立 record 模块验收清单

````

交付物：

```text
1. docs/record/enterprise-roadmap.md
2. docs/record/api-contract.md
3. docs/record/schema-contract.md
4. docs/record/permission-contract.md
5. docs/record/acceptance-checklist.md
````

---

## Phase 1：工程基线重建

目标：先让项目结构、构建、测试、CI 稳定。

做什么：

```text
1. 确认 root package scripts 全部指向 web/portal
2. 增加 aiops-server with-portal 打包 profile
3. 保留 with-console 作为回退 profile
4. 清理 web/portal 内不可用的 record 占位代码
5. 后端保留 aiops-work-record 模块，但重做 service / api / repository 边界
6. 建立后端测试基线
7. 建立前端测试基线
8. CI 加入 portal build / lint / test
9. CI 加入 aiops-work-record test
10. CI 加入 aiops-platform test
```

验收：

```text
mvn -pl modules/aiops-work-record -am test 通过
mvn -pl modules/aiops-platform -am test 通过
mvn -pl apps/aiops-server -am test 通过
pnpm -C web/portal run build 通过
pnpm -C web/portal run lint 通过
pnpm -C web/portal run test 通过
```

---

## Phase 2：平台基础能力完善

目标：先把工作记录依赖的平台能力做稳。

做什么：

```text
1. 重做 / 加固用户管理页面
2. 重做 / 加固角色权限页面
3. 建立权限码初始化机制
4. 建立角色默认授权机制
5. 完善平台字典表
6. 完善平台字典 API
7. 完善平台字典 portal 页面
8. 增加工作日历表
9. 增加工作日历 API
10. 增加工作日历 CSV 导入
11. 增加平台能力审计日志
```

平台字典必须支持：

```text
1. 字典类型列表
2. 字典类型新增
3. 字典类型编辑
4. 字典类型启用 / 禁用
5. 字典项列表
6. 字典项新增
7. 字典项编辑
8. 字典项启用 / 禁用
9. includeDisabled 查询
10. 默认字典初始化
```

工作日历先做：

```text
1. 创建年度工作日历
2. 导入日期 CSV
3. 单日覆盖
4. 查询某天是否工作日
5. 查询日期范围
6. 统计范围内工作日数量
```

---

## Phase 3：数据库模型重做

目标：把数据模型做成可长期维护的企业级结构。

做什么：

```text
1. 重新整理 migration
2. 明确是否继续 public.wr_*，还是迁移到 work_record schema
3. 如果要企业级，建议正式迁移到 work_record schema
4. 建立 wr_template
5. 建立 wr_template_version
6. 建立 wr_template_field
7. 建立 wr_record
8. 建立 wr_record_snapshot
9. 建立 wr_record_audit_event
10. 建立必要索引
11. 建立 JSONB 查询索引
12. 建立唯一约束和外键约束
```

企业级建议表：

```text
platform_dict_type
platform_dict_item
platform_calendar
platform_calendar_day

work_record.wr_template
work_record.wr_template_version
work_record.wr_template_field
work_record.wr_record
work_record.wr_record_snapshot
work_record.wr_record_audit_event
```

必须解决：

```text
1. 模板要有版本
2. 历史记录要绑定模板版本
3. 字段删除只能禁用
4. field_code 创建后不可随意改
5. 字典项只能禁用，不能物理删除
6. 记录删除走 deleted_at
7. 所有表必须 tenant_id 隔离
```

---

## Phase 4：后端领域模型重做

目标：把后端从 CRUD 做成清晰领域边界。

做什么：

```text
1. 重做 WorkRecordTemplateService
2. 重做 WorkRecordTemplateVersionService
3. 重做 WorkRecordSchemaService
4. 重做 WorkRecordFieldIndexService
5. 重做 WorkRecordService
6. 重做 WorkRecordQueryService
7. 重做 WorkRecordExportService
8. 重做 WorkRecordPermissionService
9. 重做 WorkRecordAuditService
10. 增加 WorkRecordDictionaryPort
11. 增加 WorkRecordCalendarPort
```

后端分层：

```text
api
application
domain
infrastructure
```

必须保证：

```text
1. api 不直接访问 repository
2. domain 不依赖 Spring Web
3. work-record 不反向依赖 alert / incident / inspection repository
4. dictionary 不依赖 work-record
5. calendar 不依赖 work-record
```

---

## Phase 5：模板与 Schema 契约重做

目标：先把“表单设计器输出什么，后端存什么，运行态读什么”定死。

做什么：

```text
1. 统一 schema 扩展协议
2. 统一 x-work-record 对象协议
3. 禁止前后端协议不一致
4. 建立 schema parser
5. 建立 schema validator
6. 建立 schema normalizer
7. 建立 field index sync
8. 建立 schema versioning
9. 建立字段编码正则
10. 建立字段保留字校验
```

统一协议：

```json
{
  "x-work-record": {
    "fieldCode": "priority",
    "fieldType": "select",
    "optionSource": "dict",
    "dictCode": "record_priority",
    "listVisible": true,
    "filterable": true,
    "exportable": true,
    "statistical": true
  }
}
```

field_code 规则：

```text
^[a-zA-Z][a-zA-Z0-9_]{0,63}$
```

必须支持：

```text
1. text
2. textarea
3. number
4. date
5. datetime
6. select
7. multi_select
8. user
9. boolean
```

---

## Phase 6：模板管理 API

目标：企业级模板生命周期，而不是只有一个 schema 保存接口。

做什么：

```text
1. 模板列表
2. 模板创建
3. 模板编辑
4. 模板启用 / 禁用
5. 模板复制
6. 模板发布
7. 模板版本列表
8. 模板版本详情
9. 模板草稿保存
10. 模板发布校验
11. 模板字段索引同步
12. 模板被记录引用后的字段锁定
```

模板状态：

```text
draft
published
disabled
archived
```

模板版本规则：

```text
1. 草稿可编辑
2. 发布后生成 version
3. 记录绑定 template_version_id
4. 已有记录引用的字段编码不可改
5. 删除字段实际为 enabled=false
6. 发布模板前必须通过 schema 校验
```

---

## Phase 7：portal 表单设计器重做

目标：重做成真正可用的企业级表单设计器。

做什么：

```text
1. 字段库
2. 画布
3. 属性面板
4. 实时预览
5. 字段排序
6. 字段复制
7. 字段删除 / 禁用
8. 字段编码锁定
9. 字段基础属性
10. 字段校验属性
11. 字典绑定属性
12. 列表展示属性
13. 筛选属性
14. 导出属性
15. 统计属性
16. 模板保存草稿
17. 模板发布
18. 模板预览
19. schema diff 提示
20. 字段被历史记录引用提示
```

设计器页面结构：

```text
左侧：字段库
中间：表单画布
右侧：属性面板
底部 / 右下：实时预览
顶部：模板选择 / 保存草稿 / 发布 / 预览 / 返回
```

第一版不做：

```text
复杂布局
子表单
公式字段
条件联动
流程审批
远程数据源
页面级低代码
```

---

## Phase 8：记录运行态重做

目标：用户可以稳定填写、编辑、查看工作记录。

做什么：

```text
1. 新建记录页
2. 编辑记录页
3. 详情页
4. 只读渲染
5. 字典选项注入
6. 字典禁用项历史回显
7. datetime 格式统一
8. user 字段选择器
9. 内置字段和动态字段分离
10. 提交前前端校验
11. 提交后后端校验
12. 保存草稿
13. 提交完成
14. 编辑权限控制
15. 详情权限控制
```

记录基础字段：

```text
title
templateId
templateVersionId
status
ownerId
creatorId
recordTime
```

动态字段：

```text
customDataJson
```

状态：

```text
draft
processing
done
archived
```

---

## Phase 9：动态字段后端校验重做

目标：所有绕过前端的非法数据都被后端挡住。

做什么：

```text
1. 未知字段拒绝
2. 禁用字段拒绝写入
3. 必填字段校验
4. number 类型校验
5. date 类型校验
6. datetime 类型校验
7. boolean 类型校验
8. select 静态选项校验
9. select 字典选项校验
10. multi_select 静态选项校验
11. multi_select 字典选项校验
12. user 字段用户存在性校验
13. recordTime 时区校验
14. templateVersionId 校验
15. tenantId 隔离校验
```

必须修掉：

```text
1. dict 字段只校验类型不校验值
2. datetime-local 和 OffsetDateTime 不一致
3. fieldCode 没有正则导致查询风险
4. customDataJson 可以写入不该写的字段
```

---

## Phase 10：记录列表重做

目标：企业级查询列表，而不是半成品表格。

做什么：

```text
1. 记录列表页
2. 我的记录
3. 全部记录
4. 今日记录
5. 本周记录
6. 本月记录
7. 最近 N 个工作日
8. 状态筛选
9. 模板筛选
10. 负责人筛选
11. 创建人筛选
12. 时间范围筛选
13. 标题搜索
14. 动态字段筛选
15. 动态列展示
16. 列显示控制
17. 列排序
18. 分页
19. URL search 同步
20. 空态
21. 加载态
22. 错误态
```

列表元数据 API 返回：

```text
templates
columns
filterFields
dictCodes
maxExportRows
quickViews
```

---

## Phase 11：动态查询安全重做

目标：JSONB 动态字段查询安全、可维护、可测试。

做什么：

```text
1. 动态筛选 DTO 标准化
2. filterable 白名单校验
3. operator 白名单校验
4. value 类型校验
5. fieldCode 正则校验
6. JSONB 查询参数化
7. 禁止直接拼接 custom_data_json->>'fieldCode'
8. number 字段按 number 比较
9. date 字段按 date 比较
10. datetime 字段按 timestamptz 比较
11. multi_select 用 JSONB containment 查询
12. exists / not exists 支持
13. 查询性能测试
```

动态操作符：

```text
text: contains / eq / exists
number: eq / gte / lte / between / exists
date: eq / gte / lte / between / exists
datetime: eq / gte / lte / between / exists
select: eq / in / exists
multi_select: in / contains_any / contains_all / exists
boolean: eq / exists
user: eq / in / exists
```

---

## Phase 12：导出能力重做

目标：导出真正可用、可控、可审计。

做什么：

```text
1. 导出当前筛选结果
2. 导出当前列
3. 导出动态字段
4. 导出字典 label
5. 导出用户显示名
6. 导出时间格式统一
7. CSV 注入防护
8. 最大导出行数限制
9. 超限提示
10. 导出审计
11. 导出权限控制
12. 字段 exportable 控制
13. 后端按 columns 真正输出数据
14. 前端导出弹窗
15. 导出前确认
```

第一版：

```text
同步 CSV 导出
默认最多 5000 行
```

第二版：

```text
异步导出任务
导出任务列表
下载中心
```

---

## Phase 13：权限体系企业级加固

目标：从“按钮隐藏”升级到“前后端一致的权限体系”。

做什么：

```text
1. 权限码初始化
2. 角色初始化
3. 菜单权限
4. 按钮权限
5. API 权限
6. 数据范围权限
7. 模板管理权限
8. 记录读取权限
9. 记录编辑权限
10. 导出权限
11. 字典权限
12. 工作日历权限
13. 权限测试
```

角色：

```text
系统管理员
记录管理员
普通用户
只读用户
```

权限码：

```text
platform:dict:read
platform:dict:write
platform:calendar:read
platform:calendar:write
platform:calendar:import

work-record:template:read
work-record:template:write
work-record:read:self
work-record:read:all
work-record:write
work-record:delete
work-record:export
```

---

## Phase 14：审计与变更追踪

目标：企业级系统必须知道谁改了什么。

做什么：

```text
1. 模板创建审计
2. 模板编辑审计
3. 模板发布审计
4. 字段新增审计
5. 字段修改审计
6. 字段禁用审计
7. 记录创建审计
8. 记录编辑审计
9. 记录删除审计
10. 记录导出审计
11. 字典修改审计
12. 工作日历导入审计
13. 工作日历覆盖审计
14. 详情页展示记录变更历史
```

审计信息：

```text
tenantId
actorId
action
resourceType
resourceId
beforeJson
afterJson
detailJson
createdAt
```

---

## Phase 15：工作日历接入工作记录

目标：让工作记录具备企业统计基础。

做什么：

```text
1. 记录列表支持最近 N 个工作日
2. 月报统计支持工作日数量
3. 日报缺失判断排除节假日
4. 调休工作日算工作日
5. 节假日记录不强制填写
6. 导出月报显示工作日天数
7. 工作日历 API 接入 record query
```

第一版只接：

```text
最近 5 个工作日
本工作月
工作日数量统计
```

---

## Phase 16：前端体验完善

目标：达到企业后台可用标准。

做什么：

```text
1. 统一 loading
2. 统一 empty state
3. 统一 error state
4. 统一 toast
5. 统一 confirm dialog
6. 统一表格 toolbar
7. 统一详情页 layout
8. 统一表单校验提示
9. 字典 label 缓存
10. 模板切换提示
11. 未保存离开提示
12. 字段删除风险提示
13. 字段编码锁定提示
14. 移动端基本可读
15. 国际化 key 补齐
```

---

## Phase 17：测试体系补齐

目标：不是能跑，而是可持续维护。

做什么：

```text
1. DictionaryServiceTest
2. CalendarServiceTest
3. WorkRecordTemplateServiceTest
4. WorkRecordSchemaServiceTest
5. WorkRecordFieldIndexServiceTest
6. WorkRecordFieldValidatorTest
7. WorkRecordFilterValidatorTest
8. WorkRecordServiceTest
9. WorkRecordQueryServiceTest
10. WorkRecordExportServiceTest
11. ControllerTest
12. RepositoryTest
13. ArchUnitTest
14. 前端 schema-builder test
15. 前端 dict injector test
16. 前端 runtime form test
17. 前端 designer panel test
18. 前端 records table test
19. 前端 export dialog test
```

必须覆盖：

```text
1. fieldCode 正则
2. 保留字段
3. 字典字段非法值
4. 禁用字典项历史回显
5. 字段删除后历史记录展示
6. 普通用户越权
7. 导出字段权限
8. 动态筛选白名单
9. JSONB 查询参数化
10. 模板版本兼容
```

---

## Phase 18：企业级验收场景

目标：用真实场景验收，而不是只看页面能打开。

做什么：

```text
1. 创建默认字典
2. 创建工作日历
3. 创建工作记录模板
4. 发布模板 v1
5. 用户填写记录
6. 管理员查看全部记录
7. 普通用户只能查看自己的记录
8. 管理员修改模板生成 v2
9. 历史记录仍按 v1 展示
10. 新记录按 v2 填写
11. 禁用字典项后历史记录仍显示 label
12. 动态字段筛选生效
13. 导出动态字段生效
14. 导出超限被拦截
15. 审计日志完整
```

---

## Phase 19：生产化加固

目标：具备上生产的基本能力。

做什么：

```text
1. 数据库索引优化
2. 慢查询检查
3. 大数据量分页测试
4. 导出限流
5. API rate limit
6. 请求体大小限制
7. schema_json 大小限制
8. custom_data_json 大小限制
9. 异常统一处理
10. 错误码规范
11. 操作日志
12. 监控指标
13. 健康检查
14. 权限回归测试
15. 数据备份策略
```

建议指标：

```text
1. 模板数量
2. 字段数量
3. 记录数量
4. 导出次数
5. 导出失败次数
6. 查询耗时
7. 慢查询次数
8. 权限拒绝次数
```

---

## Phase 20：后续增强

第一版企业级闭环完成后再做。

做什么：

```text
1. Excel 导入记录
2. 异步导出任务
3. 评论时间线
4. 附件上传
5. 统计报表
6. 工作量分析
7. 日报缺失提醒
8. 值班交接
9. 关联告警
10. 关联巡检
11. 关联事件
12. AI 自动总结
13. AI 月报生成
14. 模板市场
15. 字段级权限
16. 审批流
17. SLA
18. 用户管理
19. 权限管理
```

其中用户管理与权限管理复用平台 IAM，不在工作记录扩展域内另建账户、角色或授权模型。Phase 20
开始前必须完成租户隔离、逐接口鉴权、管理员防自锁、密码重置、角色授权和安全审计基线。

---

# 推荐执行顺序

```text
第 1 周：
  Phase 0
  Phase 1
  Phase 2
  Phase 3

第 2 周：
  Phase 4
  Phase 5
  Phase 6
  Phase 7

第 3 周：
  Phase 8
  Phase 9
  Phase 10
  Phase 11

第 4 周：
  Phase 12
  Phase 13
  Phase 14
  Phase 15

第 5 周：
  Phase 16
  Phase 17
  Phase 18
  Phase 19
```

最终路线就是：

```text
先重建工程基线
再做平台基础
再做模板和 schema
再做设计器
再做记录运行态
再做列表筛选导出
最后补权限、审计、测试、生产化
```
