package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemCreateRequest;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemActionItemStatusRequest;
import io.aegisops.execution.dto.PostmortemGenerateRequest;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostmortemServiceTest {
  @Test
  void generatePostmortemReport() {
    FakePostmortemRepository repository = new FakePostmortemRepository();

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            PostmortemJson.defaultMapper());

    var response =
        service.generate(
            "tenant_1",
            "inc_1",
            new PostmortemGenerateRequest("alice", true, true, true, true, true, true));

    assertEquals("generated", response.status());
    assertTrue(response.markdown().contains("# Postmortem Report"));
    assertTrue(response.sections().size() >= 7);
    assertTrue(response.actionItems().size() > 0);
  }

  @Test
  void rejectGenerateWhenIncidentNotFound() {
    PostmortemService service =
        new PostmortemService(
            new FakePostmortemRepository(),
            new FakePostmortemSourceRepository(false),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            PostmortemJson.defaultMapper());

    assertThrows(
        AppException.class,
        () ->
            service.generate(
                "tenant_1",
                "inc_missing",
                new PostmortemGenerateRequest("alice", true, true, true, true, true, true)));
  }

  @Test
  void createManualActionItem() {
    FakePostmortemRepository repository = new FakePostmortemRepository();
    repository.report =
        report("pmr_1", "tenant_1", "inc_1", "generated");

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            PostmortemJson.defaultMapper());

    var response =
        service.createActionItem(
            "tenant_1",
            "pmr_1",
            new PostmortemActionItemCreateRequest(
                "Update runbook",
                "Add rollback step",
                "bob",
                "high",
                LocalDate.now().plusDays(7),
                "manual",
                null,
                "alice"));

    assertEquals("Update runbook", response.title());
    assertEquals("open", response.status());
  }

  @Test
  void updateActionItemStatus() {
    FakePostmortemRepository repository = new FakePostmortemRepository();
    repository.report = report("pmr_1", "tenant_1", "inc_1", "generated");

    PostmortemActionItemRecord item =
        new PostmortemActionItemRecord(
            "pmai_1",
            "tenant_1",
            "pmr_1",
            "Update runbook",
            null,
            "bob",
            "high",
            "open",
            null,
            "manual",
            null,
            "alice",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    repository.actionItems.add(item);

    PostmortemService service =
        new PostmortemService(
            repository,
            new FakePostmortemSourceRepository(true),
            new PostmortemDraftBuilder(),
            new PostmortemMarkdownBuilder(),
            PostmortemJson.defaultMapper());

    var response =
        service.updateActionItemStatus(
            "tenant_1",
            "pmai_1",
            new PostmortemActionItemStatusRequest("done", "bob"));

    assertEquals("done", response.status());
  }

  private static PostmortemReportRecord report(
      String id, String tenantId, String incidentId, String status) {
    return new PostmortemReportRecord(
        id,
        tenantId,
        incidentId,
        status,
        "high",
        "Postmortem",
        "summary",
        "impact",
        "root cause",
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
        OffsetDateTime.now());
  }

  private static class FakePostmortemSourceRepository implements PostmortemSourceRepository {
    private final boolean found;

    FakePostmortemSourceRepository(boolean found) {
      this.found = found;
    }

    @Override
    public Optional<PostmortemSourceBundle> load(String tenantId, String incidentId) {
      if (!found) {
        return Optional.empty();
      }

      return Optional.of(
          new PostmortemSourceBundle(
              new PostmortemSourceBundle.IncidentSnapshot(
                  incidentId,
                  "Order service error",
                  "resolved",
                  "high",
                  OffsetDateTime.now(),
                  OffsetDateTime.now()),
              List.of(
                  new PostmortemSourceBundle.RcaSnapshot(
                      "rca_1", "rca summary", "db timeout", "0.8", OffsetDateTime.now())),
              List.of(
                  new PostmortemSourceBundle.AiDiagnosisSnapshot(
                      "ai_1",
                      "ai summary",
                      "db timeout",
                      "restart service",
                      OffsetDateTime.now())),
              List.of(
                  new PostmortemSourceBundle.ExecutionSnapshot(
                      "exec_1",
                      "live",
                      "normal",
                      "succeeded",
                      "restart service",
                      OffsetDateTime.now(),
                      OffsetDateTime.now())),
              List.of(),
              List.of(
                  new PostmortemSourceBundle.TimelineSnapshot(
                      "tl_1",
                      "incident_created",
                      "Incident created",
                      "created",
                      OffsetDateTime.now()))));
    }
  }

  private static class FakePostmortemRepository implements PostmortemRepository {
    PostmortemReportRecord report;
    final List<PostmortemSectionRecord> sections = new ArrayList<>();
    final List<PostmortemActionItemRecord> actionItems = new ArrayList<>();

    @Override
    public void createReport(PostmortemReportCreateCommand command) {
      report =
          new PostmortemReportRecord(
              command.id(),
              command.tenantId(),
              command.incidentId(),
              command.status(),
              command.severity(),
              command.title(),
              command.summary(),
              command.impact(),
              command.rootCause(),
              command.detection(),
              command.resolution(),
              command.prevention(),
              command.markdown(),
              command.sourceSnapshotJson(),
              command.generatedBy(),
              OffsetDateTime.now(),
              null,
              null,
              null,
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSection(PostmortemSectionCreateCommand command) {
      sections.add(
          new PostmortemSectionRecord(
              command.id(),
              command.tenantId(),
              command.postmortemId(),
              command.sectionOrder(),
              command.sectionType(),
              command.title(),
              command.content(),
              command.metadataJson(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
      return Optional.ofNullable(report);
    }

    @Override
    public Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId) {
      return Optional.ofNullable(report);
    }

    @Override
    public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
      return sections;
    }

    @Override
    public void createActionItem(PostmortemActionItemCreateCommand command) {
      actionItems.add(
          new PostmortemActionItemRecord(
              command.id(),
              command.tenantId(),
              command.postmortemId(),
              command.title(),
              command.description(),
              command.owner(),
              command.priority(),
              command.status(),
              command.dueDate(),
              command.sourceType(),
              command.sourceRefId(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
      return actionItems;
    }

    @Override
    public Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId) {
      return actionItems.stream().filter(item -> item.id().equals(actionItemId)).findFirst();
    }

    @Override
    public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) {
      Optional<PostmortemActionItemRecord> found = findActionItem(tenantId, actionItemId);
      if (found.isEmpty()) {
        return false;
      }

      PostmortemActionItemRecord old = found.get();
      actionItems.remove(old);
      actionItems.add(
          new PostmortemActionItemRecord(
              old.id(),
              old.tenantId(),
              old.postmortemId(),
              old.title(),
              old.description(),
              old.owner(),
              old.priority(),
              status,
              old.dueDate(),
              old.sourceType(),
              old.sourceRefId(),
              old.createdBy(),
              old.createdAt(),
              OffsetDateTime.now()));
      return true;
    }
  }
}
