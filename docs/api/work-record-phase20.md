---
title: 工作记录 Phase 20 异步与协作 API
type: api
status: accepted
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - modules/aiops-work-record/src/main/java/io/aegisops/workrecord/api
  - apps/aiops-server/src/main/resources/db/migration/V0030__init_phase20_async_foundation.sql
  - apps/aiops-server/src/main/resources/db/migration/V0031__init_phase20_import_export.sql
  - apps/aiops-server/src/main/resources/db/migration/V0032__init_phase20_collaboration.sql
---

# 工作记录 Phase 20 异步与协作 API

所有接口要求有效 JWT，租户仅从认证上下文读取。异步任务、上传会话、附件、评论和关联对象均按 `tenant_id` 隔离；嵌套资源还会校验所属工作记录，跨租户或父子资源不匹配统一按不可见处理。

## Excel 导入与异步导出

| 方法 | 路径                                           | 权限                                             | 用途                           |
| ---- | ---------------------------------------------- | ------------------------------------------------ | ------------------------------ |
| POST | `/api/work-record/imports/uploads`             | `work-record:import`                             | 创建一次性 Excel 直传会话      |
| POST | `/api/work-record/imports`                     | `work-record:import`                             | 消费上传会话并创建导入任务     |
| POST | `/api/work-record/async-exports`               | `work-record:export`、`work-record:export:async` | 创建异步导出任务               |
| GET  | `/api/work-record/async-jobs`                  | 工作记录读取权限                                 | 查询当前用户的任务列表         |
| GET  | `/api/work-record/async-jobs/{jobId}`          | 工作记录读取权限                                 | 查询任务详情与逐行结果         |
| POST | `/api/work-record/async-jobs/{jobId}/cancel`   | 工作记录读取权限                                 | 取消尚未结束的本人任务         |
| POST | `/api/work-record/async-jobs/{jobId}/download` | 工作记录读取权限                                 | 获取短时效、禁止缓存的下载地址 |

上传会话只能由创建者消费一次。Excel 公式不执行，逐行结果具有幂等键；导出文件由 worker 流式生成并写入对象存储。

## 评论、附件与关联对象

以下路径均以 `/api/work-record/records/{recordId}` 为前缀。

| 方法       | 路径                                   | 权限                              | 用途                                 |
| ---------- | -------------------------------------- | --------------------------------- | ------------------------------------ |
| GET/POST   | `/comments`                            | 读取权限 / `work-record:comment`  | 查询或新增评论                       |
| PUT/DELETE | `/comments/{commentId}`                | `work-record:comment`             | 修改或删除本人评论；管理员需审核权限 |
| GET        | `/attachments`                         | 工作记录读取权限                  | 查询可见附件                         |
| POST       | `/attachments/uploads`                 | `work-record:attachment`          | 创建附件直传会话                     |
| POST       | `/attachments`                         | `work-record:attachment`          | 完成上传并进入异步扫描               |
| POST       | `/attachments/{attachmentId}/download` | 工作记录读取权限                  | 下载已通过扫描的附件                 |
| DELETE     | `/attachments/{attachmentId}`          | `work-record:attachment`          | 删除本人附件；管理员需审核权限       |
| GET/POST   | `/relations`                           | 读取权限 / `work-record:relation` | 查询或新增告警、巡检、事件关联       |
| DELETE     | `/relations/{relationId}`              | `work-record:relation`            | 删除关联                             |

附件状态为 `pending_scan`、`ready`、`quarantined` 或 `deleted`。只有 `ready` 状态可下载；大小异常或检测到可执行文件头时进入隔离。关联创建时会再次校验目标域读取权限，并保存当时的标题、状态和摘要快照。
