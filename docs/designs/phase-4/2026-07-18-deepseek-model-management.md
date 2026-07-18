---
title: DeepSeek 模型管理页面设计
type: design
status: accepted
phase: phase-4
owner: ai
created: 2026-07-18
updated: 2026-07-18
related:
  - docs/api/ai-models.md
  - docs/ai-agent-design.md
  - docs/adr/0003-aiops-agent-boundary.md
---

# DeepSeek 模型管理页面设计

## 范围

本次补齐租户级 AI 模型配置管理，只支持 DeepSeek。管理员可以新增、编辑、启停、设为默认、测试连接和删除模型配置。

不在本次范围内：多供应商、从供应商自动同步模型、计费统计、Prompt 管理，以及把数据库配置动态下发给 `aiops-agent`。诊断运行时继续使用既有环境变量配置，后续通过独立的内部契约变更接入默认模型，避免在 UI 需求中隐式扩大密钥分发边界。

## 数据与安全

- `ai_model_config` 按 `tenant_id` 隔离，供应商固定为 `deepseek`。
- API Key 使用 AES-256-GCM 加密后写入 `encrypted_api_key`，密钥来自服务端环境变量，禁止进入响应和审计快照。
- 响应只返回 `apiKeyConfigured`，不返回掩码密钥，避免泄露长度或片段。
- 同一租户最多一个默认模型；禁用或删除默认模型时清除默认标记。
- 全部端点要求 `admin:manage`，写操作记录 `ai_model.*` 审计事件。

## 页面结构

页面位于 `/platform/ai-models`，归入“平台管理”。桌面端使用紧凑表格，移动端使用纵向信息块；主操作为“添加模型”，行操作包括编辑、测试连接、设为默认、启停和删除。

表单固定展示 DeepSeek 供应商，支持名称、模型标识、API 地址、API Key 和启用状态。编辑时 API Key 留空表示保留原值。

## 验收

- 无 `admin:manage` 权限时导航不可见且路由拒绝访问。
- 新增配置后列表可见，API Key 不回显。
- 可测试 DeepSeek `/models` 连接，并返回明确成功或失败信息。
- 默认模型唯一，禁用默认模型后不再是默认模型。
- 删除、启停、设默认和密钥变更都有审计记录。
