package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseCreateCommand;
import io.aegisops.execution.dto.IncidentCaseCreateFromPostmortemRequest;
import io.aegisops.execution.dto.IncidentCasePublishRequest;
import io.aegisops.execution.dto.IncidentCaseRecord;
import io.aegisops.execution.dto.IncidentCaseResolutionStepCreateCommand;
import io.aegisops.execution.dto.IncidentCaseResolutionStepRecord;
import io.aegisops.execution.dto.IncidentCaseSymptomCreateCommand;
import io.aegisops.execution.dto.IncidentCaseSymptomRecord;
import io.aegisops.execution.dto.IncidentCaseTagCreateCommand;
import io.aegisops.execution.dto.IncidentCaseTagRecord;
import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentCaseServiceTest {
  @Test
  void createCaseFromPostmortem() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    var response =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of("order-service")));

    assertEquals("draft", response.status());
    assertEquals(80, response.qualityScore());
    assertTrue(response.tags().contains("order-service"));
    assertTrue(response.symptoms().size() > 0);
    assertTrue(response.resolutionSteps().size() > 0);
  }

  @Test
  void rejectDuplicateCaseForPostmortem() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    service.createFromPostmortem(
        "tenant_1", "pmr_1", new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    assertThrows(
        AppException.class,
        () ->
            service.createFromPostmortem(
                "tenant_1",
                "pmr_1",
                new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of())));
  }

  @Test
  void publishCase() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    var created =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    var published =
        service.publish("tenant_1", created.id(), new IncidentCasePublishRequest("reviewer"));

    assertEquals("published", published.status());
    assertEquals("reviewer", published.reviewedBy());
  }

  @Test
  void rejectPublishWhenQualityTooLow() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    var created =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 30, List.of()));

    assertThrows(
        AppException.class,
        () ->
            service.publish("tenant_1", created.id(), new IncidentCasePublishRequest("reviewer")));
  }

  @Test
  void listDefaultsToPublishedCases() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    var created =
        service.createFromPostmortem(
            "tenant_1",
            "pmr_1",
            new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    service.publish("tenant_1", created.id(), new IncidentCasePublishRequest("reviewer"));

    var result = service.list("tenant_1", null, null, 20);

    assertEquals("published", caseRepository.capturedListStatus);
    assertEquals(1, result.size());
    assertEquals("published", result.get(0).status());
  }

  @Test
  void listAllCasesWhenStatusAll() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    service.createFromPostmortem(
        "tenant_1", "pmr_1", new IncidentCaseCreateFromPostmortemRequest("alice", 80, List.of()));

    var result = service.list("tenant_1", "all", null, 20);

    assertEquals(null, caseRepository.capturedListStatus);
    assertEquals(1, result.size());
  }

  @Test
  void listNormalizesTagFilter() {
    FakeIncidentCaseRepository caseRepository = new FakeIncidentCaseRepository();
    FakePostmortemRepository postmortemRepository = new FakePostmortemRepository();

    IncidentCaseService service =
        new IncidentCaseService(
            caseRepository, postmortemRepository, new IncidentCaseDraftBuilder());

    service.list("tenant_1", "published", "Order Service", 20);

    assertEquals("order-service", caseRepository.capturedListTag);
  }

  private static class FakeIncidentCaseRepository implements IncidentCaseRepository {
    IncidentCaseRecord caseRecord;
    final List<IncidentCaseSymptomRecord> symptoms = new ArrayList<>();
    final List<IncidentCaseResolutionStepRecord> steps = new ArrayList<>();
    final List<IncidentCaseTagRecord> tags = new ArrayList<>();
    String capturedListStatus;
    String capturedListTag;
    int capturedListLimit;

    @Override
    public void createCase(IncidentCaseCreateCommand command) {
      caseRecord =
          new IncidentCaseRecord(
              command.id(),
              command.tenantId(),
              command.sourcePostmortemId(),
              command.incidentId(),
              command.status(),
              command.severity(),
              command.title(),
              command.summary(),
              command.rootCause(),
              command.resolution(),
              command.prevention(),
              command.qualityScore(),
              command.createdBy(),
              null,
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSymptom(IncidentCaseSymptomCreateCommand command) {
      symptoms.add(
          new IncidentCaseSymptomRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.symptomType(),
              command.name(),
              command.description(),
              OffsetDateTime.now()));
    }

    @Override
    public void createResolutionStep(IncidentCaseResolutionStepCreateCommand command) {
      steps.add(
          new IncidentCaseResolutionStepRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.stepOrder(),
              command.title(),
              command.description(),
              command.actionType(),
              command.sourceRefId(),
              OffsetDateTime.now()));
    }

    @Override
    public void createTag(IncidentCaseTagCreateCommand command) {
      tags.add(
          new IncidentCaseTagRecord(
              command.id(),
              command.tenantId(),
              command.caseId(),
              command.tag(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<IncidentCaseRecord> findCase(String tenantId, String caseId) {
      return Optional.ofNullable(caseRecord).filter(item -> item.id().equals(caseId));
    }

    @Override
    public Optional<IncidentCaseRecord> findByPostmortem(String tenantId, String postmortemId) {
      return Optional.ofNullable(caseRecord)
          .filter(item -> item.sourcePostmortemId().equals(postmortemId));
    }

    @Override
    public Optional<IncidentCaseRecord> findLatestByIncident(String tenantId, String incidentId) {
      return Optional.ofNullable(caseRecord).filter(item -> item.incidentId().equals(incidentId));
    }

    @Override
    public List<IncidentCaseRecord> listCases(
        String tenantId, String status, String tag, int limit) {
      capturedListStatus = status;
      capturedListTag = tag;
      capturedListLimit = limit;

      if (caseRecord == null) {
        return List.of();
      }

      if (status != null && !status.equals(caseRecord.status())) {
        return List.of();
      }

      return List.of(caseRecord);
    }

    @Override
    public List<IncidentCaseSymptomRecord> listSymptoms(String tenantId, String caseId) {
      return symptoms;
    }

    @Override
    public List<IncidentCaseResolutionStepRecord> listResolutionSteps(
        String tenantId, String caseId) {
      return steps;
    }

    @Override
    public List<IncidentCaseTagRecord> listTags(String tenantId, String caseId) {
      return tags;
    }

    @Override
    public boolean publish(String tenantId, String caseId, String reviewer) {
      if (caseRecord == null || !"draft".equals(caseRecord.status())) {
        return false;
      }

      caseRecord =
          new IncidentCaseRecord(
              caseRecord.id(),
              caseRecord.tenantId(),
              caseRecord.sourcePostmortemId(),
              caseRecord.incidentId(),
              "published",
              caseRecord.severity(),
              caseRecord.title(),
              caseRecord.summary(),
              caseRecord.rootCause(),
              caseRecord.resolution(),
              caseRecord.prevention(),
              caseRecord.qualityScore(),
              caseRecord.createdBy(),
              reviewer,
              OffsetDateTime.now(),
              null,
              caseRecord.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public boolean archive(String tenantId, String caseId) {
      if (caseRecord == null) {
        return false;
      }

      caseRecord =
          new IncidentCaseRecord(
              caseRecord.id(),
              caseRecord.tenantId(),
              caseRecord.sourcePostmortemId(),
              caseRecord.incidentId(),
              "archived",
              caseRecord.severity(),
              caseRecord.title(),
              caseRecord.summary(),
              caseRecord.rootCause(),
              caseRecord.resolution(),
              caseRecord.prevention(),
              caseRecord.qualityScore(),
              caseRecord.createdBy(),
              caseRecord.reviewedBy(),
              caseRecord.publishedAt(),
              OffsetDateTime.now(),
              caseRecord.createdAt(),
              OffsetDateTime.now());
      return true;
    }
  }

  private static class FakePostmortemRepository implements PostmortemRepository {
    @Override
    public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
      return Optional.of(
          new PostmortemReportRecord(
              postmortemId,
              tenantId,
              "inc_1",
              "generated",
              "high",
              "Postmortem - Order service error",
              "summary",
              "impact",
              "db timeout",
              "detection",
              "resolution",
              "prevention",
              "# md",
              "{}",
              "alice",
              OffsetDateTime.now(),
              null,
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
      return List.of(
          new PostmortemSectionRecord(
              "pms_1",
              tenantId,
              postmortemId,
              1,
              "impact",
              "Impact",
              "High error rate",
              "{}",
              OffsetDateTime.now()),
          new PostmortemSectionRecord(
              "pms_2",
              tenantId,
              postmortemId,
              2,
              "resolution",
              "Resolution",
              "Restart service",
              "{}",
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
      return List.of();
    }

    @Override
    public void createReport(PostmortemReportCreateCommand command) {}

    @Override
    public void createSection(PostmortemSectionCreateCommand command) {}

    @Override
    public Optional<PostmortemReportRecord> findLatestByIncident(
        String tenantId, String incidentId) {
      return Optional.empty();
    }

    @Override
    public void createActionItem(PostmortemActionItemCreateCommand command) {}

    @Override
    public Optional<PostmortemActionItemRecord> findActionItem(
        String tenantId, String actionItemId) {
      return Optional.empty();
    }

    @Override
    public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) {
      return false;
    }
  }
}
