package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.DataScope;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Covers review/list access-control paths that rely on {@link AiGenerationAccessGuard}. */
class AiGenerationAccessTest {
  @Test
  void reviewRequiresDedicatedPermission() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(),
            mock(OutboxWriter.class),
            new ObjectMapper(),
            mock(WorkRecordAuditService.class));

    assertThatThrownBy(
            () -> service.review("tenant-1", "ai-1", true, principal("work-record:read:all")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void reviewerWithTenantWideReadCanListPeriodReports() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecord visible = mock(WorkRecord.class);
    ObjectMapper mapper = new ObjectMapper();
    UserPrincipal principal =
        principal(PermissionCodes.WORK_RECORD_AI_REVIEW, PermissionCodes.WORK_RECORD_READ_ALL);
    when(repository.listByResource("tenant-1", "tenant_week", "2026-07-13"))
        .thenReturn(List.of(generation("success", GENERATION_INPUT_WITH_SECRET)));
    when(records.get("tenant-1", "record-1", principal)).thenReturn(visible);
    when(visible.customDataJson()).thenReturn("{\"secret\":\"value\"}");
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(records, mapper),
            mock(OutboxWriter.class),
            mapper,
            mock(WorkRecordAuditService.class));

    assertThat(service.list("tenant-1", "tenant_week", "2026-07-13", principal)).hasSize(1);
  }

  @Test
  void reviewerCannotListPeriodReportsWithoutTenantWideRead() {
    var service =
        new AiGenerationService(
            mock(AiGenerationRepository.class),
            mock(AiInputBuilder.class),
            guard(),
            mock(OutboxWriter.class),
            new ObjectMapper(),
            mock(WorkRecordAuditService.class));

    assertThatThrownBy(
            () ->
                service.list(
                    "tenant-1",
                    "tenant_month",
                    "2026-07",
                    principal(PermissionCodes.WORK_RECORD_AI_REVIEW)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void reviewAuditsTheDecisionWithoutGeneratedContent() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordAuditService audit = mock(WorkRecordAuditService.class);
    when(repository.review("tenant-1", "ai-1", "accepted", "user-1")).thenReturn(true);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation("accepted")));
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(),
            mock(OutboxWriter.class),
            new ObjectMapper(),
            audit);

    service.review(
        "tenant-1",
        "ai-1",
        true,
        principal(PermissionCodes.WORK_RECORD_AI_REVIEW, PermissionCodes.WORK_RECORD_READ_ALL));

    verify(audit)
        .record(
            "tenant-1",
            null,
            null,
            "work_record_ai_generation",
            "ai-1",
            WorkRecordAuditActions.AI_GENERATION_REVIEWED,
            "user-1",
            "{\"status\":\"accepted\"}");
  }

  @Test
  void reviewerCannotReviewPeriodReportWithoutTenantWideRead() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation("success")));
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(),
            mock(OutboxWriter.class),
            new ObjectMapper(),
            mock(WorkRecordAuditService.class));

    assertThatThrownBy(
            () ->
                service.review(
                    "tenant-1", "ai-1", true, principal(PermissionCodes.WORK_RECORD_AI_REVIEW)))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).review(any(), any(), any(), any());
  }

  @Test
  void reviewerCannotListOrReviewPeriodReportWithSelfDataScope() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation("success")));
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(),
            mock(OutboxWriter.class),
            new ObjectMapper(),
            mock(WorkRecordAuditService.class));
    UserPrincipal principal =
        selfScopePrincipal(
            PermissionCodes.WORK_RECORD_AI_REVIEW, PermissionCodes.WORK_RECORD_READ_ALL);

    assertThatThrownBy(() -> service.list("tenant-1", "tenant_week", "2026-07-13", principal))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.review("tenant-1", "ai-1", true, principal))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).review(any(), any(), any(), any());
  }

  @Test
  void reviewerCannotListOrReviewGenerationContainingUnreadableFields() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecord visible = mock(WorkRecord.class);
    ObjectMapper mapper = new ObjectMapper();
    AiGeneration generation = generation("success", GENERATION_INPUT_WITH_SECRET);
    UserPrincipal principal =
        principal(PermissionCodes.WORK_RECORD_AI_REVIEW, PermissionCodes.WORK_RECORD_READ_ALL);
    when(repository.listByResource("tenant-1", "tenant_week", "2026-07-13"))
        .thenReturn(List.of(generation));
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation));
    when(records.get("tenant-1", "record-1", principal)).thenReturn(visible);
    when(visible.customDataJson()).thenReturn("{}");
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(records, mapper),
            mock(OutboxWriter.class),
            mapper,
            mock(WorkRecordAuditService.class));

    assertThatThrownBy(() -> service.list("tenant-1", "tenant_week", "2026-07-13", principal))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.review("tenant-1", "ai-1", true, principal))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).review(any(), any(), any(), any());
  }

  @Test
  void reviewerCannotListOrReviewGenerationWhenVisibleFieldValueIsMasked() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecord visible = mock(WorkRecord.class);
    ObjectMapper mapper = new ObjectMapper();
    AiGeneration generation = generation("success", GENERATION_INPUT_WITH_SECRET);
    UserPrincipal principal =
        principal(PermissionCodes.WORK_RECORD_AI_REVIEW, PermissionCodes.WORK_RECORD_READ_ALL);
    when(repository.listByResource("tenant-1", "tenant_week", "2026-07-13"))
        .thenReturn(List.of(generation));
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation));
    when(records.get("tenant-1", "record-1", principal)).thenReturn(visible);
    when(visible.customDataJson()).thenReturn("{\"secret\":\"******\"}");
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            guard(records, mapper),
            mock(OutboxWriter.class),
            mapper,
            mock(WorkRecordAuditService.class));

    assertThatThrownBy(() -> service.list("tenant-1", "tenant_week", "2026-07-13", principal))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.review("tenant-1", "ai-1", true, principal))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository, never()).review(any(), any(), any(), any());
  }

  private static AiGenerationAccessGuard guard() {
    return new AiGenerationAccessGuard(
        mock(WorkRecordQueryService.class), new WorkRecordPermissionService(), new ObjectMapper());
  }

  private static AiGenerationAccessGuard guard(
      WorkRecordQueryService records, ObjectMapper objectMapper) {
    return new AiGenerationAccessGuard(records, new WorkRecordPermissionService(), objectMapper);
  }

  private static UserPrincipal principal(String... permissions) {
    return principalWithScope(DataScope.ALL, permissions);
  }

  private static UserPrincipal selfScopePrincipal(String... permissions) {
    return principalWithScope(DataScope.SELF, permissions);
  }

  private static UserPrincipal principalWithScope(DataScope scope, String... permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of(permissions),
        Map.of("work-record", scope));
  }

  private static AiGeneration generation(String status) {
    return generation(status, EMPTY_GENERATION_INPUT);
  }

  private static AiGeneration generation(String status, String inputJson) {
    return new AiGeneration(
        "ai-1",
        "tenant-1",
        "monthly_report",
        "tenant_month",
        "2026-07",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        status,
        "v1",
        "hash",
        inputJson,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "[]",
        null,
        "user-1",
        null,
        null,
        OffsetDateTime.parse("2026-07-14T00:00:00Z"),
        null);
  }

  private static final String EMPTY_GENERATION_INPUT =
      """
      {"contractVersion":"work-record-generation.v1","generationType":"monthly_report","tenantId":"tenant-1","resourceId":"2026-07","actorId":"user-1","periodStart":null,"periodEnd":null,"locale":"zh-CN","promptVersion":"work-record-monthly-v1","records":[],"statistics":{},"traceId":"trace-1"}
      """;

  private static final String GENERATION_INPUT_WITH_SECRET =
      """
      {"contractVersion":"work-record-generation.v1","generationType":"weekly_report","tenantId":"tenant-1","resourceId":"2026-07-13","actorId":"user-1","periodStart":null,"periodEnd":null,"locale":"zh-CN","promptVersion":"work-record-weekly-v1","records":[{"id":"record-1","title":"Record","status":"done","recordTime":null,"ownerName":"Alice","fields":{"secret":"value"},"relations":[]}],"statistics":{},"traceId":"trace-1"}
      """;
}
