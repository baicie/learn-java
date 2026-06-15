package io.aegisops.ai.client.dto;

import java.util.List;

public record AgentDiagnosisRequest(
    String tenantId,
    String incidentId,
    AgentIncidentContext incident,
    List<AgentAlertContext> alerts,
    AgentRcaContext rca,
    String locale,
    String traceId) {}
