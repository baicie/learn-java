package io.aegisops.runner.executor.ansible;

import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;

/** 把执行 Ansible step 所需的 4 类资源打包，便于跨方法传递并满足 checkstyle 5 参数限制。 */
public record AnsibleInputs(
    AnsibleInventoryRecord inventory,
    AnsiblePlaybookRecord playbook,
    AnsiblePolicyRecord policy,
    AnsibleActionPayload payload) {}
