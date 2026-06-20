package io.aegisops.execution;

import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.Optional;

public interface AgentEvalSourceRepository {
  Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(String tenantId, String incidentId);

  IncidentCaseResponse getIncidentCase(String tenantId, String caseId);
}
