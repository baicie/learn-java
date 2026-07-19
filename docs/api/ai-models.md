---
title: AI 模型管理 API
type: api
status: accepted
phase: phase-4
owner: ai
created: 2026-07-18
updated: 2026-07-18
related:
  - docs/designs/phase-4/2026-07-18-deepseek-model-management.md
---

# AI 模型管理 API

所有端点使用统一 `ApiResponse<T>` 包装，要求租户上下文与 `admin:manage` 权限。

## 端点

| 方法     | 路径                          | 说明                                    |
| -------- | ----------------------------- | --------------------------------------- |
| `GET`    | `/api/ai/models`              | 查询当前租户模型配置                    |
| `POST`   | `/api/ai/models`              | 新增 DeepSeek 模型配置                  |
| `PUT`    | `/api/ai/models/{id}`         | 更新模型配置；`apiKey` 为空时保留原密钥 |
| `DELETE` | `/api/ai/models/{id}`         | 删除模型配置                            |
| `POST`   | `/api/ai/models/{id}/test`    | 调用 DeepSeek `/models` 测试连接        |
| `POST`   | `/api/ai/models/{id}/default` | 将启用的模型设为租户默认模型            |

## 写入结构

```json
{
  "provider": "deepseek",
  "name": "DeepSeek 生产模型",
  "modelName": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "secret",
  "enabled": true
}
```

`provider` 当前只接受 `deepseek`。新增时 `apiKey` 必填；更新时省略或传空字符串表示保留已有密钥。

## 响应结构

```json
{
  "id": "aim_xxx",
  "provider": "deepseek",
  "name": "DeepSeek 生产模型",
  "modelName": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com/v1",
  "enabled": true,
  "defaultModel": true,
  "apiKeyConfigured": true,
  "lastTestStatus": "success",
  "lastTestMessage": "连接成功",
  "lastTestedAt": "2026-07-18T12:00:00Z",
  "createdAt": "2026-07-18T12:00:00Z",
  "updatedAt": "2026-07-18T12:00:00Z"
}
```

响应与审计数据均不得包含 `apiKey` 或 `encryptedApiKey`。
