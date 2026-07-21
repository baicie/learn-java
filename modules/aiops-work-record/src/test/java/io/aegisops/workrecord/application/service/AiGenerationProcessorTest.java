package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationResponse;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiGenerationProcessorTest {
  @Test
  void persistsProviderTraceAndAuditsFallback() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordAiClient client = mock(WorkRecordAiClient.class);
    WorkRecordAuditService audit = mock(WorkRecordAuditService.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation()));
    when(repository.markRunning("tenant-1", "ai-1")).thenReturn(true);
    when(repository.complete(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    when(client.generate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new WorkRecordGenerationResponse(
                "work-record-generation.v1",
                "deterministic",
                "langgraph-deterministic",
                "v1",
                "# 月报",
                java.util.List.of("Dify 生成失败"),
                null,
                "workflow-1",
                "version-1",
                null,
                null,
                "http_503",
                java.util.Map.of()));
    var processor = new AiGenerationProcessor(repository, client, new ObjectMapper(), audit);

    processor.process("tenant-1", "ai-1", false);

    verify(repository)
        .complete(
            argThat(
                command ->
                    command.tenantId().equals("tenant-1")
                        && command.id().equals("ai-1")
                        && command.providerWorkflowId().equals("workflow-1")
                        && command.providerWorkflowVersion().equals("version-1")
                        && command.warningsJson().contains("Dify 生成失败")
                        && command.fallbackReason().equals("http_503")));
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("work_record_ai_generation"),
            org.mockito.ArgumentMatchers.eq("ai-1"),
            org.mockito.ArgumentMatchers.eq(WorkRecordAuditActions.AI_GENERATION_FALLBACK),
            org.mockito.ArgumentMatchers.eq("user-1"),
            org.mockito.ArgumentMatchers.contains("http_503"));
  }

  @Test
  void returnsRunningGenerationToQueuedWhenAttemptCanRetry() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordAiClient client = mock(WorkRecordAiClient.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation()));
    when(repository.markRunning("tenant-1", "ai-1")).thenReturn(true);
    when(client.generate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("temporary"));
    var processor =
        new AiGenerationProcessor(
            repository, client, new ObjectMapper(), mock(WorkRecordAuditService.class));

    assertThatThrownBy(() -> processor.process("tenant-1", "ai-1", false))
        .isInstanceOf(IllegalStateException.class);

    verify(repository).markRetrying("tenant-1", "ai-1");
  }

  @Test
  void marksGenerationFailedWhenLastAttemptFails() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordAiClient client = mock(WorkRecordAiClient.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation()));
    when(repository.markRunning("tenant-1", "ai-1")).thenReturn(true);
    when(client.generate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("temporary"));
    var processor =
        new AiGenerationProcessor(
            repository, client, new ObjectMapper(), mock(WorkRecordAuditService.class));

    assertThatThrownBy(() -> processor.process("tenant-1", "ai-1", true))
        .isInstanceOf(IllegalStateException.class);

    verify(repository).fail("tenant-1", "ai-1");
  }

  private static AiGeneration generation() {
    return new AiGeneration(
        "ai-1",
        "tenant-1",
        "monthly_report",
        "tenant_month",
        "2026-07",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        "queued",
        "v1",
        "hash",
        "{\"contractVersion\":\"work-record-generation.v1\",\"generationType\":\"monthly_report\",\"tenantId\":\"tenant-1\",\"resourceId\":\"2026-07\",\"locale\":\"zh-CN\",\"promptVersion\":\"v1\",\"records\":[],\"statistics\":{},\"traceId\":\"trace-1\"}",
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
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        null);
  }
}
