package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;

/** Runtime JSON helpers. */
public record AnsibleRuntime(ObjectMapper objectMapper, AnsibleJson json) {}
