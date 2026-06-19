package io.aegisops.execution;

import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import java.util.List;
import java.util.Optional;

public interface IncidentCaseRepository {
  void createCase(IncidentCaseCreateCommand command);

  void createSymptom(IncidentCaseSymptomCreateCommand command);

  void createResolutionStep(IncidentCaseResolutionStepCreateCommand command);

  void createTag(IncidentCaseTagCreateCommand command);

  Optional<IncidentCaseRecord> findCase(String tenantId, String caseId);

  Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId);

  Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId);

  List<IncidentCaseRecord> listCases(String tenantId, String status, String tag, int limit);

  List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId);

  List<IncidentCaseResolutionStepRecord> listResolutionSteps(String tenantId, String caseId);

  List<IncidentCaseTagRecord> listTags(String tenantId, String caseId);

  boolean publish(String tenantId, String caseId, String reviewer);

  boolean archive(String tenantId, String caseId);
}
