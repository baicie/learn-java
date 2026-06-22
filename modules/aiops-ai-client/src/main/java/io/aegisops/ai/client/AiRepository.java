package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentEvalResultCommand;
import io.aegisops.ai.client.dto.AgentEvalResultRecord;
import io.aegisops.ai.client.dto.AgentRunRecord;
import io.aegisops.ai.client.dto.AgentRunStepCommand;
import io.aegisops.ai.client.dto.AgentRunStepRecord;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiEvidenceRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.AiTimelineRecord;
import io.aegisops.ai.client.dto.SaveAgentRunCommand;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.util.List;
import java.util.Optional;

public interface AiRepository {
  Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId);

  List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId);

  List<AiEvidenceRecord> listDiagnosisEvidence(String tenantId, String incidentId);

  List<AiTimelineRecord> listIncidentTimeline(String tenantId, String incidentId, int limit);

  Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId);

  Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId);

  void saveDiagnosis(SaveDiagnosisCommand command);

  void addIncidentTimeline(TimelineCommand command);

  default void saveAgentRun(SaveAgentRunCommand command) {}

  default void saveAgentRunSteps(List<AgentRunStepCommand> commands) {}

  default void saveAgentEvalResults(List<AgentEvalResultCommand> commands) {}

  default Optional<AgentRunRecord> findLatestAgentRun(String tenantId, String incidentId) {
    return Optional.empty();
  }

  default List<AgentRunStepRecord> listAgentRunSteps(String runId) {
    return List.of();
  }

  default List<AgentEvalResultRecord> listAgentEvalResults(String runId) {
    return List.of();
  }
}
