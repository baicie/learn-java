---
title: Phase5.6 Ansible Sandbox Execution
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase5.6 Ansible Sandbox Execution

## 目标

Phase5.6 将 Phase5.5 的 Ansible dry-run preview 升级为真实 `ansible-playbook --check` 沙箱执行。

## 安全边界

允许：

- ProcessBuilder(List<String>)
- ansible-playbook --check
- isolated workspace
- stdout/stderr artifact

禁止：

- Runtime.exec
- sh -c
- cmd /c
- SSH key
- Vault
- live ansible
- 非 --check 执行

## 执行流程

1. runner 收到 actionType=ansible step
2. 加载 inventory/playbook/policy
3. 校验 policy.allow_check_execution
4. 创建 workspace
5. 写入 inventory.ini
6. 写入 playbook.yml
7. 构建 argv
8. 执行 ansible-playbook --check
9. 捕获 stdout/stderr
10. 写 execution_artifact
11. 清理 workspace

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

## 后续

Phase5.7 才考虑 Ansible live execution，并且必须增加：

- approval guard
- command policy
- credential isolation
- vault handling
- rollback plan
