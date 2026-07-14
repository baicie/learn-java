---
title: 当前账户 API
type: api
status: accepted
phase: phase-21
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - modules/aiops-security/src/main/java/io/aegisops/security/AuthController.java
---

# 当前账户 API

统一前缀：`/api/auth`。除登录外均要求有效 JWT，只允许操作当前登录用户。

| 方法 | 路径                        | 用途                   |
| ---- | --------------------------- | ---------------------- |
| GET  | `/api/auth/profile`         | 查询当前用户资料       |
| PUT  | `/api/auth/profile`         | 修改显示名称和邮箱     |
| POST | `/api/auth/change-password` | 校验当前密码后修改密码 |

密码最少 8 位；修改成功会写入安全审计事件 `auth.password.change`。
