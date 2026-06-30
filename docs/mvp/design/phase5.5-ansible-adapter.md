---
title: Phase5.5 Ansible Adapter
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase5.5 Ansible Adapter

## 目标

Phase5.5 接入 Ansible Adapter 第一版。

本阶段只做:

- inventory 管理
- playbook 管理
- execution policy
- dry-run preview
- artifact 记录

不做:

- ansible-playbook 真实执行
- SSH 凭证
- Ansible Vault
- ProcessBuilder
- Runtime.exec

## 数据表

- ansible_inventory
- ansible_playbook
- ansible_execution_policy

## action_payload

```json
{
  "inventoryId": "inv_1",
  "playbookId": "pb_1",
  "checkMode": true,
  "tags": ["restart"],
  "extraVars": {
    "service_name": "order-service"
  }
}
```

## 安全策略

- inventory 必须启用
- playbook 必须启用
- policy 必须启用
- inventory 必须在 allowed_inventory_ids
- tags 必须在 playbook.allowed_tags
- extraVars key 必须在 policy.allowed_extra_vars
- secret-like extraVars 被拒绝
  - ansible_password
  - ansible_become_password
  - ansible_ssh_private_key_file
  - ansible_private_key_file
  - ssh_key
  - private_key
  - password
  - token
  - secret

## live

Phase5.5 不实现 live Ansible。

即使 live-enabled = true，仍返回:

```txt
ANSIBLE_LIVE_NOT_IMPLEMENTED
```

## API

| 方法 | 路径                                  | 说明                             |
| ---- | ------------------------------------- | -------------------------------- |
| GET  | /api/ansible-inventories              | 列出 inventory                   |
| POST | /api/ansible-inventories              | 创建 inventory                   |
| GET  | /api/ansible-inventories/{id}         | 查询 inventory                   |
| POST | /api/ansible-inventories/{id}/enable  | 启用                             |
| POST | /api/ansible-inventories/{id}/disable | 停用                             |
| GET  | /api/ansible-playbooks                | 列出 playbook                    |
| POST | /api/ansible-playbooks                | 创建 playbook（自动创建 policy） |
| GET  | /api/ansible-playbooks/{id}           | 查询 playbook                    |
| POST | /api/ansible-playbooks/{id}/enable    | 启用                             |
| POST | /api/ansible-playbooks/{id}/disable   | 停用                             |

## Runner 流程

1. 解析 action_payload
2. 查询 inventory / playbook / policy
3. dry-run 走 validateDryRun + previewBuilder，生成 `ansible-dry-run-preview.json`
4. live 但 live-enabled=false 时返回失败 artifact
5. live + live-enabled=true 时抛 `ANSIBLE_LIVE_NOT_IMPLEMENTED`

## 后续

Phase5.6 做 Ansible Sandbox Execution（真实 ansible-playbook --check）。
