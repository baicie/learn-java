package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;

@FunctionalInterface
public interface DiagnosisGrantProvider {
  String issue(AgentDiagnosisRequest request);
}
