package io.aegisops.execution;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunFinishCommand;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import java.util.List;
import java.util.Optional;

public interface AgentEvalRepository {
  void createDataset(AgentEvalDatasetCreateCommand command);

  Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId);

  List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status);

  boolean updateDatasetStatus(
      String tenantId, String datasetId, String fromStatus, String toStatus);

  void createCase(AgentEvalCaseCreateCommand command);

  List<AgentEvalCaseRecord> listCases(String tenantId, String datasetId, boolean onlyEnabled);

  Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId);

  void createPromptProfile(AgentPromptProfileCreateCommand command);

  Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId);

  List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status);

  boolean archivePromptProfile(String tenantId, String profileId);

  void createRun(AgentEvalRunCreateCommand command);

  Optional<AgentEvalRunRecord> findRun(String tenantId, String runId);

  boolean finishRun(AgentEvalRunFinishCommand command);

  void createCaseResult(AgentEvalCaseResultCreateCommand command);

  List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId);
}
