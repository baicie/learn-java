package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AiGenerationServiceTest {
  @Test
  void createsMonthlyGenerationAndEnqueuesWorkerJob() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    AiInputBuilder inputs = mock(AiInputBuilder.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    var service =
        new AiGenerationService(
            repository, inputs, mock(WorkRecordQueryService.class), outbox, new ObjectMapper());
    when(inputs.monthlyReport(any(), any(), any(), any()))
        .thenReturn(generationRequest("monthly_report"));
    when(repository.findReusable(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
    when(repository.create(any())).thenAnswer(invocation -> generation("queued"));

    AiGeneration result =
        service.requestMonthlyReport(
            "tenant-1",
            LocalDate.of(2026, 7, 14),
            principal(
                PermissionCodes.WORK_RECORD_AI_GENERATE, PermissionCodes.WORK_RECORD_READ_ALL));

    assertThat(result.status()).isEqualTo("queued");
    verify(outbox).enqueue(any(OutboxMessage.class));
  }

  @Test
  void reusesIdenticalGenerationWithoutDuplicateJob() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    AiInputBuilder inputs = mock(AiInputBuilder.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    var service =
        new AiGenerationService(
            repository, inputs, mock(WorkRecordQueryService.class), outbox, new ObjectMapper());
    when(inputs.recordSummary(any(), any(), any(), any()))
        .thenReturn(generationRequest("record_summary"));
    when(repository.findReusable(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(generation("success")));

    assertThat(
            service.requestRecordSummary(
                "tenant-1", "record-1", principal(PermissionCodes.WORK_RECORD_AI_GENERATE)))
        .extracting(AiGeneration::status)
        .isEqualTo("success");
    verify(repository, never()).create(any());
    verify(outbox, never()).enqueue(any(OutboxMessage.class));
  }

  @Test
  void ignoresTraceIdWhenHashingIdenticalBusinessInput() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    AiInputBuilder inputs = mock(AiInputBuilder.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    var service =
        new AiGenerationService(
            repository, inputs, mock(WorkRecordQueryService.class), outbox, new ObjectMapper());
    when(inputs.recordSummary(any(), any(), any(), any()))
        .thenReturn(generationRequest("record_summary", "trace-1"))
        .thenReturn(generationRequest("record_summary", "trace-2"));
    java.util.concurrent.atomic.AtomicReference<String> firstHash =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(repository.findReusable(any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              String hash = invocation.getArgument(4);
              if (firstHash.get() == null) {
                firstHash.set(hash);
                return Optional.empty();
              }
              return firstHash.get().equals(hash)
                  ? Optional.of(generation("success"))
                  : Optional.empty();
            });
    when(repository.create(any())).thenAnswer(invocation -> generation("queued"));

    UserPrincipal principal = principal(PermissionCodes.WORK_RECORD_AI_GENERATE);
    service.requestRecordSummary("tenant-1", "record-1", principal);
    AiGeneration reused = service.requestRecordSummary("tenant-1", "record-1", principal);

    assertThat(reused.status()).isEqualTo("success");
    verify(repository, times(1)).create(any());
    verify(outbox, times(1)).enqueue(any());
  }

  @Test
  void reviewRequiresDedicatedPermission() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    var service =
        new AiGenerationService(
            repository,
            mock(AiInputBuilder.class),
            mock(WorkRecordQueryService.class),
            mock(OutboxWriter.class),
            new ObjectMapper());

    assertThatThrownBy(
            () -> service.review("tenant-1", "ai-1", true, principal("work-record:read:all")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void monthlyGenerationRequiresTenantWideReadPermission() {
    var service =
        new AiGenerationService(
            mock(AiGenerationRepository.class),
            mock(AiInputBuilder.class),
            mock(WorkRecordQueryService.class),
            mock(OutboxWriter.class),
            new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.requestMonthlyReport(
                    "tenant-1",
                    LocalDate.of(2026, 7, 1),
                    principal(PermissionCodes.WORK_RECORD_AI_GENERATE)))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static UserPrincipal principal(String... permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of(permissions),
        Map.of());
  }

  private static WorkRecordGenerationRequest generationRequest(String type) {
    return generationRequest(type, "trace-1");
  }

  private static WorkRecordGenerationRequest generationRequest(String type, String traceId) {
    return new WorkRecordGenerationRequest(
        "work-record-generation.v1",
        type,
        "tenant-1",
        "resource-1",
        null,
        null,
        "zh-CN",
        "prompt-v1",
        java.util.List.of(),
        Map.of(),
        traceId);
  }

  private static AiGeneration generation(String status) {
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
        "{}",
        null,
        null,
        null,
        "user-1",
        null,
        null,
        OffsetDateTime.parse("2026-07-14T00:00:00Z"),
        null);
  }
}
