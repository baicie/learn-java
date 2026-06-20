package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResponse;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultResponse;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalDatasetResponse;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunFinishCommand;
import io.aegisops.execution.dto.AgentEvalRunResponse;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileCreateRequest;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import io.aegisops.execution.dto.AgentPromptProfileResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvalService {
  private final AgentEvalRepository repository;
  private final AgentEvalSourceRepository sourceRepository;
  private final AgentEvalScorer scorer;
  private final AgentEvalSupport support;
  private final AgentEvalJson json;

  public AgentEvalService(
      AgentEvalRepository repository,
      AgentEvalSourceRepository sourceRepository,
      AgentEvalScorer scorer,
      AgentEvalSupport support,
      AgentEvalJson json) {
    this.repository = repository;
    this.sourceRepository = sourceRepository;
    this.scorer = scorer;
    this.support = support;
    this.json = json;
  }

  @Transactional
  public AgentEvalDatasetResponse createDataset(
      String tenantId, AgentEvalDatasetCreateRequest request) {
    support.requireDatasetCreateRequest(request);
    support.requireDatasetName(request.name());

    String id = support.newId("aeds");
    repository.createDataset(
        new AgentEvalDatasetCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.description(),
            "draft",
            support.blankToDefault(request.createdBy(), "system")));

    return support.toDatasetResponse(loadDataset(tenantId, id));
  }

  public List<AgentEvalDatasetResponse> listDatasets(String tenantId, String status) {
    String normalized = support.normalizeDatasetStatusOrNull(status);
    return repository.listDatasets(tenantId, normalized).stream()
        .map(support::toDatasetResponse)
        .toList();
  }

  public AgentEvalDatasetResponse getDataset(String tenantId, String datasetId) {
    return support.toDatasetResponse(loadDataset(tenantId, datasetId));
  }

  @Transactional
  public AgentEvalDatasetResponse activateDataset(String tenantId, String datasetId) {
    boolean updated = repository.updateDatasetStatus(tenantId, datasetId, "draft", "active");
    support.requireDatasetActivated(updated);
    return getDataset(tenantId, datasetId);
  }

  @Transactional
  public AgentEvalDatasetResponse archiveDataset(String tenantId, String datasetId) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    boolean updated =
        repository.updateDatasetStatus(tenantId, dataset.id(), dataset.status(), "archived");
    support.requireDatasetArchived(updated);
    return getDataset(tenantId, datasetId);
  }

  @Transactional
  public AgentEvalCaseResponse createCase(
      String tenantId, String datasetId, AgentEvalCaseCreateRequest request) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    support.requireDatasetMutable(dataset);
    support.validateCaseRequest(request);

    String id = support.newId("aec");
    repository.createCase(
        new AgentEvalCaseCreateCommand(
            id,
            tenantId,
            datasetId,
            support.normalizeCaseSourceType(request.sourceType()),
            request.sourceId(),
            request.incidentId(),
            request.title().trim(),
            support.normalizeSeverity(request.severity()),
            request.inputContext().trim(),
            request.expectedRootCause(),
            json.write(support.listOrEmpty(request.expectedKeywords())),
            json.write(support.listOrEmpty(request.expectedActions())),
            json.write(support.listOrEmpty(request.forbiddenActions())),
            json.write(support.listOrEmpty(request.tags())),
            true,
            support.blankToDefault(request.createdBy(), "system")));

    return support.toCaseResponse(
        repository
            .findCase(tenantId, id)
            .orElseThrow(
                () -> new AppException("AGENT_EVAL_CASE_NOT_FOUND", "Eval case not found")));
  }

  @Transactional
  public AgentEvalCaseResponse createCaseFromIncidentCase(
      String tenantId,
      String datasetId,
      String caseId,
      AgentEvalCaseFromIncidentCaseRequest request) {
    AgentEvalDatasetRecord dataset = loadDataset(tenantId, datasetId);
    support.requireDatasetMutable(dataset);

    IncidentCaseResponse incidentCase = sourceRepository.getIncidentCase(tenantId, caseId);
    support.requirePublishedIncidentCase(incidentCase.status());

    List<String> keywords =
        support.mergeLists(
            incidentCase.tags(),
            List.of(support.value(incidentCase.rootCause())),
            request == null ? List.of() : support.listOrEmpty(request.extraKeywords()));

    return createCase(
        tenantId,
        datasetId,
        new AgentEvalCaseCreateRequest(
            "incident_case",
            caseId,
            incidentCase.incidentId(),
            incidentCase.title(),
            incidentCase.severity(),
            support.buildInputContext(incidentCase),
            incidentCase.rootCause(),
            keywords,
            List.of(support.value(incidentCase.resolution())),
            request == null ? List.of() : support.listOrEmpty(request.forbiddenActions()),
            incidentCase.tags(),
            request == null ? "system" : support.blankToDefault(request.createdBy(), "system")));
  }

  public List<AgentEvalCaseResponse> listCases(String tenantId, String datasetId) {
    loadDataset(tenantId, datasetId);
    return repository.listCases(tenantId, datasetId, false).stream()
        .map(support::toCaseResponse)
        .toList();
  }

  @Transactional
  public AgentPromptProfileResponse createPromptProfile(
      String tenantId, AgentPromptProfileCreateRequest request) {
    support.validatePromptProfileRequest(request);

    String id = support.newId("appf");
    repository.createPromptProfile(
        new AgentPromptProfileCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.version().trim(),
            "active",
            request.systemPrompt().trim(),
            request.diagnosisPromptTemplate().trim(),
            json.write(request.metadata()),
            support.blankToDefault(request.createdBy(), "system")));

    return support.toPromptProfileResponse(
        repository
            .findPromptProfile(tenantId, id)
            .orElseThrow(
                () ->
                    new AppException(
                        "AGENT_PROMPT_PROFILE_NOT_FOUND", "Prompt profile not found")));
  }

  public List<AgentPromptProfileResponse> listPromptProfiles(String tenantId, String status) {
    String normalized = support.normalizePromptProfileStatusOrNull(status);
    return repository.listPromptProfiles(tenantId, normalized).stream()
        .map(support::toPromptProfileResponse)
        .toList();
  }

  public AgentPromptProfileResponse getPromptProfile(String tenantId, String profileId) {
    return support.toPromptProfileResponse(loadPromptProfile(tenantId, profileId));
  }

  @Transactional
  public AgentPromptProfileResponse archivePromptProfile(String tenantId, String profileId) {
    boolean updated = repository.archivePromptProfile(tenantId, profileId);
    support.requirePromptProfileArchived(updated);
    return getPromptProfile(tenantId, profileId);
  }

  @Transactional
  public AgentEvalRunResponse runEval(String tenantId, AgentEvalRunCreateRequest request) {
    support.requireRunCreateRequest(request);
    support.requireDatasetId(request.datasetId());

    AgentEvalDatasetRecord dataset = loadDataset(tenantId, request.datasetId());
    support.requireActiveDataset(dataset);

    String mode = support.normalizeEvalMode(request.mode());
    ensurePromptProfileActive(tenantId, request.promptProfileId());

    List<AgentEvalCaseRecord> cases = repository.listCases(tenantId, dataset.id(), true);
    support.requireCasesNotEmpty(cases);

    String runId = support.newId("aer");
    repository.createRun(
        new AgentEvalRunCreateCommand(
            runId,
            tenantId,
            dataset.id(),
            support.blankToNull(request.promptProfileId()),
            mode,
            "running",
            0,
            0,
            0,
            0.0d,
            null,
            support.blankToDefault(request.createdBy(), "system")));

    RunStats stats = scoreAndPersist(tenantId, runId, mode, cases);

    boolean updated =
        repository.finishRun(
            new AgentEvalRunFinishCommand(
                tenantId,
                runId,
                stats.status,
                stats.total,
                stats.passed,
                stats.failed,
                stats.average,
                stats.summary));
    support.requireRunFinished(updated);

    return getRun(tenantId, runId);
  }

  public AgentEvalRunResponse getRun(String tenantId, String runId) {
    return support.toRunResponse(
        repository
            .findRun(tenantId, runId)
            .orElseThrow(() -> new AppException("AGENT_EVAL_RUN_NOT_FOUND", "Eval run not found")));
  }

  public List<AgentEvalCaseResultResponse> listRunResults(String tenantId, String runId) {
    getRun(tenantId, runId);
    return repository.listCaseResults(tenantId, runId).stream()
        .map(support::toCaseResultResponse)
        .toList();
  }

  private void ensurePromptProfileActive(String tenantId, String profileId) {
    if (profileId == null || profileId.isBlank()) {
      return;
    }
    AgentPromptProfileRecord profile = loadPromptProfile(tenantId, profileId);
    support.requireActivePromptProfile(profile.status());
  }

  private RunStats scoreAndPersist(
      String tenantId, String runId, String mode, List<AgentEvalCaseRecord> cases) {
    int passed = 0;
    int failed = 0;
    double totalScore = 0.0d;

    for (AgentEvalCaseRecord evalCase : cases) {
      AgentDiagnosisSnapshot actual = resolveActualDiagnosis(tenantId, mode, evalCase);
      AgentEvalScore score = scoreOneCase(evalCase, actual);
      totalScore += score.score();
      if (score.passed()) {
        passed++;
      } else {
        failed++;
      }
      persistCaseResult(tenantId, runId, evalCase, actual, score);
    }

    int total = cases.size();
    double average = total == 0 ? 0.0d : support.round(totalScore / total);
    String status = failed == 0 ? "succeeded" : "failed";
    String summary =
        "total=" + total + ", passed=" + passed + ", failed=" + failed + ", average=" + average;
    return new RunStats(total, passed, failed, average, status, summary);
  }

  private AgentEvalScore scoreOneCase(AgentEvalCaseRecord evalCase, AgentDiagnosisSnapshot actual) {
    return scorer.score(
        new AgentEvalScoringInputs(
            new AgentEvalScoringInputs.AgentEvalExpected(
                evalCase.expectedRootCause(),
                json.readStringList(evalCase.expectedKeywordsJson()),
                json.readStringList(evalCase.expectedActionsJson()),
                json.readStringList(evalCase.forbiddenActionsJson())),
            new AgentEvalScoringInputs.AgentEvalActual(
                actual.summary(), actual.rootCause(), actual.recommendation())));
  }

  private void persistCaseResult(
      String tenantId,
      String runId,
      AgentEvalCaseRecord evalCase,
      AgentDiagnosisSnapshot actual,
      AgentEvalScore score) {
    repository.createCaseResult(
        new AgentEvalCaseResultCreateCommand(
            support.newId("aecr"),
            tenantId,
            runId,
            evalCase.id(),
            actual.id(),
            actual.summary(),
            actual.rootCause(),
            actual.recommendation(),
            score.score(),
            score.rootCauseScore(),
            score.keywordScore(),
            score.actionScore(),
            score.safetyScore(),
            score.passed(),
            json.write(
                Map.of(
                    "matchedKeywords", score.matchedKeywords(),
                    "missingKeywords", score.missingKeywords(),
                    "forbiddenHits", score.forbiddenHits()))));
  }

  private AgentDiagnosisSnapshot resolveActualDiagnosis(
      String tenantId, String mode, AgentEvalCaseRecord evalCase) {
    if ("mock".equals(mode)) {
      return new AgentDiagnosisSnapshot(
          "mock_" + evalCase.id(),
          evalCase.incidentId(),
          evalCase.inputContext(),
          evalCase.expectedRootCause(),
          String.join("\n", json.readStringList(evalCase.expectedActionsJson())),
          null);
    }

    if (evalCase.incidentId() == null || evalCase.incidentId().isBlank()) {
      return new AgentDiagnosisSnapshot(null, null, "", "", "", null);
    }

    return sourceRepository
        .findLatestDiagnosis(tenantId, evalCase.incidentId())
        .orElseGet(() -> new AgentDiagnosisSnapshot(null, evalCase.incidentId(), "", "", "", null));
  }

  private AgentEvalDatasetRecord loadDataset(String tenantId, String datasetId) {
    return repository
        .findDataset(tenantId, datasetId)
        .orElseThrow(() -> new AppException("AGENT_EVAL_DATASET_NOT_FOUND", "Dataset not found"));
  }

  private AgentPromptProfileRecord loadPromptProfile(String tenantId, String profileId) {
    return repository
        .findPromptProfile(tenantId, profileId)
        .orElseThrow(
            () -> new AppException("AGENT_PROMPT_PROFILE_NOT_FOUND", "Prompt profile not found"));
  }

  private record RunStats(
      int total, int passed, int failed, double average, String status, String summary) {}
}
