package io.aegisops.ai.client.dto;

import java.util.List;

public record AgentDiagnosisRequest(
    String contractVersion,
    String tenantId,
    String incidentId,
    AgentIncidentContext incident,
    List<AgentAlertContext> alerts,
    AgentRcaContext rca,
    List<AgentEvidenceContext> evidence,
    List<AgentTimelineContext> timeline,
    String locale,
    String traceId) {}
