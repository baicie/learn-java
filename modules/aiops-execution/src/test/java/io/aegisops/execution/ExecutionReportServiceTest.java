package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportGenerateRequest;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionRunResponse;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionReportServiceTest {
  @Test
  void generateReportForTerminalExecution() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.generate(
            "tenant_1",
            "exec_1",
            new ExecutionReportGenerateRequest("standard", "alice", true, true, true));

    assertEquals("generated", response.status());
    assertTrue(response.markdown().contains("# Execution Report"));
    assertEquals(5, response.sections().size());
    assertTrue(
        repository.auditEvents.stream()
            .anyMatch(item -> "report_generated".equals(item.eventType())));
  }

  @Test
  void rejectReportForNonTerminalExecution() {
    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("running"),
            new FakeExecutionReportRepository(),
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.generate(
                "tenant_1",
                "exec_1",
                new ExecutionReportGenerateRequest("standard", "alice", true, true, true)));
  }

  @Test
  void createVerificationWritesAuditEvent() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.createVerification(
            "tenant_1",
            "exec_1",
            new io.aegisops.execution.dto.ExecutionVerificationCreateRequest(
                null,
                "after",
                "service",
                "order-service",
                "passed",
                "service recovered",
                Map.of("httpStatus", 200),
                "alice"));

    assertEquals("passed", response.status());
    assertEquals(1, repository.verifications.size());
    assertTrue(
        repository.auditEvents.stream()
            .anyMatch(item -> "verification_created".equals(item.eventType())));
  }

  @Test
  void generateReportCanExcludeArtifacts() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.generate(
            "tenant_1",
            "exec_1",
            new ExecutionReportGenerateRequest("standard", "alice", false, true, true));

    assertTrue(response.markdown().contains("No artifacts."));
    assertTrue(
        response.sections().stream()
            .filter(section -> "artifacts".equals(section.sectionType()))
            .findFirst()
            .orElseThrow()
            .content()
            .contains("No artifacts."));
  }

  @Test
  void generatedReportContainsReportGeneratedAuditEvent() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    var response =
        service.generate(
            "tenant_1",
            "exec_1",
            new ExecutionReportGenerateRequest("standard", "alice", true, true, true));

    assertTrue(response.markdown().contains("report_generated"));
    assertTrue(
        response.sections().stream()
            .filter(section -> "audit".equals(section.sectionType()))
            .findFirst()
            .orElseThrow()
            .content()
            .contains("report_generated"));
  }

  @Test
  void rejectVerificationStepIdOutsideExecution() {
    FakeExecutionReportRepository repository = new FakeExecutionReportRepository();

    ExecutionReportService service =
        new ExecutionReportService(
            new FakeExecutionRequestService("succeeded"),
            repository,
            new ExecutionReportMarkdownBuilder(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.createVerification(
                "tenant_1",
                "exec_1",
                new io.aegisops.execution.dto.ExecutionVerificationCreateRequest(
                    "step_other",
                    "after",
                    "service",
                    "order-service",
                    "passed",
                    "service recovered",
                    Map.of("httpStatus", 200),
                    "alice")));
  }

  private static class FakeExecutionRequestService extends ExecutionRequestService {
    private final String status;

    FakeExecutionRequestService(String status) {
      super(null, new ExecutionProperties(), new ObjectMapper());
      this.status = status;
    }

    @Override
    public ExecutionRunResponse getExecution(String tenantId, String executionId) {
      return ExecutionReportTestFixtures.execution(status);
    }
  }

  private static class FakeExecutionReportRepository implements ExecutionReportRepository {
    ExecutionReportRecord report;
    final List<ExecutionReportSectionRecord> sections = new ArrayList<>();
    final List<ExecutionVerificationRecord> verifications = new ArrayList<>();
    final List<ExecutionAuditEventRecord> auditEvents = new ArrayList<>();

    @Override
    public void createReport(ExecutionReportCreateCommand command) {
      report =
          new ExecutionReportRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.reportType(),
              command.status(),
              command.title(),
              command.summary(),
              command.markdown(),
              command.generatedBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public void createSection(ExecutionReportSectionCreateCommand command) {
      sections.add(
          new ExecutionReportSectionRecord(
              command.id(),
              command.tenantId(),
              command.reportId(),
              command.sectionOrder(),
              command.sectionType(),
              command.title(),
              command.content(),
              command.metadataJson(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<ExecutionReportRecord> findReport(String tenantId, String reportId) {
      return Optional.ofNullable(report);
    }

    @Override
    public Optional<ExecutionReportRecord> findLatestReportByExecution(
        String tenantId, String executionId) {
      return Optional.ofNullable(report);
    }

    @Override
    public List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId) {
      return sections;
    }

    @Override
    public void createVerification(ExecutionVerificationCreateCommand command) {
      verifications.add(
          new ExecutionVerificationRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.stepId(),
              command.verificationType(),
              command.targetType(),
              command.targetId(),
              command.status(),
              command.summary(),
              command.detailsJson(),
              command.createdBy(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionVerificationRecord> listVerifications(
        String tenantId, String executionId) {
      return verifications;
    }

    @Override
    public void createAuditEvent(ExecutionAuditEventCreateCommand command) {
      auditEvents.add(
          new ExecutionAuditEventRecord(
              command.id(),
              command.tenantId(),
              command.executionId(),
              command.stepId(),
              command.eventType(),
              command.actor(),
              command.summary(),
              command.payloadJson(),
              OffsetDateTime.now()));
    }

    @Override
    public List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId) {
      return auditEvents;
    }
  }
}
