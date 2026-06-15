package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;

public interface AiAgentClient {
  AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request);
}
