---
title: 平台用户与权限管理 API
type: api
status: accepted
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - modules/aiops-platform/src/main/java/io/aegisops/platform/iam/web/PlatformUserController.java
  - modules/aiops-platform/src/main/java/io/aegisops/platform/iam/web/PlatformRoleController.java
  - modules/aiops-platform/src/main/java/io/aegisops/platform/iam/web/PlatformPermissionController.java
---

# 平台用户与权限管理 API

所有接口要求有效 JWT，并从认证上下文取得 `tenant_id` 与操作者 ID。客户端不得提交或覆盖租户；资源不存在与跨租户访问统一按不可见处理。

## 用户管理

前缀：`/api/platform/users`。

| 方法 | 路径                   | 权限                           | 用途                       |
| ---- | ---------------------- | ------------------------------ | -------------------------- |
| GET  | `/`                    | `platform:user:read`           | 分页查询用户               |
| GET  | `/{id}`                | `platform:user:read`           | 查询用户详情               |
| POST | `/`                    | `platform:user:write`          | 新增用户并可分配初始角色   |
| PUT  | `/{id}`                | `platform:user:write`          | 修改显示名和邮箱           |
| POST | `/{id}/status`         | `platform:user:status`         | 启用、禁用、锁定或解锁用户 |
| POST | `/{id}/reset-password` | `platform:user:reset-password` | 管理员重置密码             |
| PUT  | `/{id}/roles`          | `platform:user:assign-role`    | 原子替换用户角色           |

密码长度为 8–128 位。系统拒绝管理员自我禁用、自我锁定、自行移除 `system_admin`，并保证每个租户至少保留一个可用系统管理员。

## 角色与权限管理

| 方法   | 路径                                     | 权限                  | 用途                     |
| ------ | ---------------------------------------- | --------------------- | ------------------------ |
| GET    | `/api/platform/roles`                    | `platform:role:read`  | 查询角色                 |
| GET    | `/api/platform/roles/{code}`             | `platform:role:read`  | 查询角色、权限和数据范围 |
| POST   | `/api/platform/roles`                    | `platform:role:write` | 新增自定义角色           |
| PUT    | `/api/platform/roles/{code}`             | `platform:role:write` | 修改角色                 |
| POST   | `/api/platform/roles/{code}/permissions` | `platform:role:write` | 原子替换角色权限         |
| POST   | `/api/platform/roles/{code}/data-scopes` | `platform:role:write` | 原子替换数据范围         |
| DELETE | `/api/platform/roles/{code}`             | `platform:role:write` | 软删除未使用的自定义角色 |
| GET    | `/api/platform/permissions`              | `platform:role:read`  | 查询分组权限目录         |

内置角色不可删除，外部调用不能创建内置角色；仍有用户引用的角色不可删除。权限和数据范围替换在单事务中完成并写审计日志。

## 主要错误码

| 错误码                           | 含义                         |
| -------------------------------- | ---------------------------- |
| `IAM_USER_NOT_FOUND`             | 用户不存在或不属于当前租户   |
| `IAM_ROLE_NOT_FOUND`             | 角色不存在或不属于当前租户   |
| `IAM_USER_SELF_STATUS_FORBIDDEN` | 禁止禁用或锁定自己           |
| `IAM_USER_SELF_ROLE_FORBIDDEN`   | 禁止移除自己的系统管理员角色 |
| `IAM_LAST_ADMIN_FORBIDDEN`       | 操作会移除最后一个可用管理员 |
| `IAM_ROLE_SYSTEM_PROTECTED`      | 内置角色禁止修改或删除       |
| `IAM_ROLE_IN_USE`                | 角色仍被用户使用             |
