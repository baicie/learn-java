package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseCreateRequest;
import io.aegisops.execution.dto.AgentEvalCaseFromIncidentCaseRequest;
import io.aegisops.execution.dto.AgentEvalCaseRecord;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultRecord;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateRequest;
import io.aegisops.execution.dto.AgentEvalDatasetRecord;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateRequest;
import io.aegisops.execution.dto.AgentEvalRunFinishCommand;
import io.aegisops.execution.dto.AgentEvalRunRecord;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentEvalServiceTest {
  @Test
  void createDatasetAndCaseThenRunMockEval() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1", new AgentEvalDatasetCreateRequest("core cases", "desc", "alice"));

    service.createCase(
        "tenant_1",
        dataset.id(),
        new AgentEvalCaseCreateRequest(
            "manual",
            null,
            "inc_1",
            "redis timeout",
            "high",
            "order service redis timeout",
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of("rm -rf"),
            List.of("redis"),
            "alice"));

    service.activateDataset("tenant_1", dataset.id());

    var run =
        service.runEval(
            "tenant_1", new AgentEvalRunCreateRequest(dataset.id(), null, "mock", "alice"));

    assertEquals("succeeded", run.status());
    assertEquals(1, run.totalCases());
    assertEquals(1, run.passedCases());
    assertEquals(1, repository.results.size());
  }

  @Test
  void rejectRunWhenDatasetNotActive() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1", new AgentEvalDatasetCreateRequest("core cases", "desc", "alice"));

    assertThrows(
        AppException.class,
        () ->
            service.runEval(
                "tenant_1", new AgentEvalRunCreateRequest(dataset.id(), null, "mock", "alice")));
  }

  @Test
  void rejectNullDatasetCreateRequest() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    assertThrows(AppException.class, () -> service.createDataset("tenant_1", null));
  }

  @Test
  void rejectNullRunCreateRequest() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    assertThrows(AppException.class, () -> service.runEval("tenant_1", null));
  }

  @Test
  void createCaseFromPublishedIncidentCase() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1", new AgentEvalDatasetCreateRequest("case dataset", "desc", "alice"));

    var evalCase =
        service.createCaseFromIncidentCase(
            "tenant_1",
            dataset.id(),
            "icase_1",
            new AgentEvalCaseFromIncidentCaseRequest(
                "alice", List.of("order-service"), List.of("rm -rf")));

    assertEquals("incident_case", evalCase.sourceType());
    assertEquals("icase_1", evalCase.sourceId());
    assertTrue(evalCase.expectedKeywordsJson().contains("redis"));
  }

  @Test
  void offlineEvalUsesLatestDiagnosis() {
    FakeAgentEvalRepository repository = new FakeAgentEvalRepository();
    AgentEvalService service = service(repository);

    var dataset =
        service.createDataset(
            "tenant_1", new AgentEvalDatasetCreateRequest("offline", "desc", "alice"));

    service.createCase(
        "tenant_1",
        dataset.id(),
        new AgentEvalCaseCreateRequest(
            "manual",
            null,
            "inc_1",
            "redis timeout",
            "high",
            "context",
            "redis timeout",
            List.of("redis", "timeout"),
            List.of("restart"),
            List.of(),
            List.of(),
            "alice"));

    service.activateDataset("tenant_1", dataset.id());

    var run =
        service.runEval(
            "tenant_1",
            new AgentEvalRunCreateRequest(dataset.id(), null, "offline_latest_diagnosis", "alice"));

    assertEquals("succeeded", run.status());
    assertEquals(1, run.passedCases());
  }

  private AgentEvalService service(FakeAgentEvalRepository repository) {
    return new AgentEvalService(
        repository,
        new FakeAgentEvalSourceRepository(),
        new AgentEvalScorer(),
        new AgentEvalSupport(),
        new AgentEvalJson(new ObjectMapper()));
  }

  private static class FakeAgentEvalSourceRepository implements AgentEvalSourceRepository {
    @Override
    public Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(
        String tenantId, String incidentId) {
      return Optional.of(
          new AgentDiagnosisSnapshot(
              "aid_1",
              incidentId,
              "redis timeout",
              "redis timeout caused issue",
              "restart service",
              OffsetDateTime.now()));
    }

    @Override
    public IncidentCaseResponse getIncidentCase(String tenantId, String caseId) {
      return new IncidentCaseResponse(
          caseId,
          tenantId,
          "pmr_1",
          "inc_1",
          "published",
          "high",
          "Order service redis timeout",
          "Order service failed due to redis timeout",
          "redis timeout",
          "restart service",
          "add timeout alert",
          80,
          "alice",
          "reviewer",
          OffsetDateTime.now(),
          null,
          List.of(
              new IncidentCaseSymptomResponse(
                  "sym_1", "impact", "High error rate", "5xx increased", OffsetDateTime.now())),
          List.of(
              new IncidentCaseResolutionStepResponse(
                  "step_1",
                  1,
                  "Restart service",
                  "restart service",
                  "manual",
                  "pms_1",
                  OffsetDateTime.now())),
          List.of("redis", "timeout"),
          OffsetDateTime.now(),
          OffsetDateTime.now());
    }
  }

  private static class FakeAgentEvalRepository implements AgentEvalRepository {
    AgentEvalDatasetRecord dataset;
    final List<AgentEvalCaseRecord> cases = new ArrayList<>();
    AgentEvalRunRecord run;
    final List<AgentEvalCaseResultRecord> results = new ArrayList<>();
    AgentPromptProfileRecord profile;

    @Override
    public void createDataset(AgentEvalDatasetCreateCommand command) {
      dataset =
          new AgentEvalDatasetRecord(
              command.id(),
              command.tenantId(),
              command.name(),
              command.description(),
              command.status(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentEvalDatasetRecord> findDataset(String tenantId, String datasetId) {
      return Optional.ofNullable(dataset).filter(item -> item.id().equals(datasetId));
    }

    @Override
    public List<AgentEvalDatasetRecord> listDatasets(String tenantId, String status) {
      return dataset == null ? List.of() : List.of(dataset);
    }

    @Override
    public boolean updateDatasetStatus(
        String tenantId, String datasetId, String fromStatus, String toStatus) {
      if (dataset == null
          || !dataset.id().equals(datasetId)
          || !dataset.status().equals(fromStatus)) {
        return false;
      }
      dataset =
          new AgentEvalDatasetRecord(
              dataset.id(),
              dataset.tenantId(),
              dataset.name(),
              dataset.description(),
              toStatus,
              dataset.createdBy(),
              dataset.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createCase(AgentEvalCaseCreateCommand command) {
      cases.add(
          new AgentEvalCaseRecord(
              command.id(),
              command.tenantId(),
              command.datasetId(),
              command.sourceType(),
              command.sourceId(),
              command.incidentId(),
              command.title(),
              command.severity(),
              command.inputContext(),
              command.expectedRootCause(),
              command.expectedKeywordsJson(),
              command.expectedActionsJson(),
              command.forbiddenActionsJson(),
              command.tagsJson(),
              command.enabled(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<AgentEvalCaseRecord> listCases(
        String tenantId, String datasetId, boolean onlyEnabled) {
      return cases.stream().filter(item -> item.datasetId().equals(datasetId)).toList();
    }

    @Override
    public Optional<AgentEvalCaseRecord> findCase(String tenantId, String caseId) {
      return cases.stream().filter(item -> item.id().equals(caseId)).findFirst();
    }

    @Override
    public void createPromptProfile(AgentPromptProfileCreateCommand command) {
      profile =
          new AgentPromptProfileRecord(
              command.id(),
              command.tenantId(),
              command.name(),
              command.version(),
              command.status(),
              command.systemPrompt(),
              command.diagnosisPromptTemplate(),
              command.metadataJson(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentPromptProfileRecord> findPromptProfile(String tenantId, String profileId) {
      return Optional.ofNullable(profile).filter(item -> item.id().equals(profileId));
    }

    @Override
    public List<AgentPromptProfileRecord> listPromptProfiles(String tenantId, String status) {
      return profile == null ? List.of() : List.of(profile);
    }

    @Override
    public boolean archivePromptProfile(String tenantId, String profileId) {
      return true;
    }

    @Override
    public void createRun(AgentEvalRunCreateCommand command) {
      run =
          new AgentEvalRunRecord(
              command.id(),
              command.tenantId(),
              command.datasetId(),
              command.promptProfileId(),
              command.mode(),
              command.status(),
              command.totalCases(),
              command.passedCases(),
              command.failedCases(),
              command.averageScore(),
              command.summary(),
              command.createdBy(),
              OffsetDateTime.now(),
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentEvalRunRecord> findRun(String tenantId, String runId) {
      return Optional.ofNullable(run).filter(item -> item.id().equals(runId));
    }

    @Override
    public boolean finishRun(AgentEvalRunFinishCommand command) {
      if (run == null || !run.id().equals(command.runId())) {
        return false;
      }
      run =
          new AgentEvalRunRecord(
              run.id(),
              run.tenantId(),
              run.datasetId(),
              run.promptProfileId(),
              run.mode(),
              command.status(),
              command.totalCases(),
              command.passedCases(),
              command.failedCases(),
              command.averageScore(),
              command.summary(),
              run.createdBy(),
              run.startedAt(),
              OffsetDateTime.now(),
              run.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createCaseResult(AgentEvalCaseResultCreateCommand command) {
      results.add(
          new AgentEvalCaseResultRecord(
              command.id(),
              command.tenantId(),
              command.runId(),
              command.caseId(),
              command.actualDiagnosisId(),
              command.actualSummary(),
              command.actualRootCause(),
              command.actualRecommendation(),
              command.score(),
              command.rootCauseScore(),
              command.keywordScore(),
              command.actionScore(),
              command.safetyScore(),
              command.passed(),
              command.detailsJson(),
              OffsetDateTime.now()));
    }

    @Override
    public List<AgentEvalCaseResultRecord> listCaseResults(String tenantId, String runId) {
      return results;
    }
  }
}
