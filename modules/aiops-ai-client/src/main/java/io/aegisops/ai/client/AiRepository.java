package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.util.List;
import java.util.Optional;

public interface AiRepository {
  Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId);

  List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId);

  Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId);

  Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId);

  void saveDiagnosis(SaveDiagnosisCommand command);

  void addIncidentTimeline(TimelineCommand command);
}
