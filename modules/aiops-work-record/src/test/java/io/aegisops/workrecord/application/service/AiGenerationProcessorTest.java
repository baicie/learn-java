package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiGenerationProcessorTest {
  @Test
  void returnsRunningGenerationToQueuedWhenAttemptCanRetry() {
    AiGenerationRepository repository = mock(AiGenerationRepository.class);
    WorkRecordAiClient client = mock(WorkRecordAiClient.class);
    when(repository.find("tenant-1", "ai-1")).thenReturn(Optional.of(generation()));
    when(repository.markRunning("tenant-1", "ai-1")).thenReturn(true);
    when(client.generate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("temporary"));
    var processor = new AiGenerationProcessor(repository, client, new ObjectMapper());

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
    var processor = new AiGenerationProcessor(repository, client, new ObjectMapper());

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
        "user-1",
        null,
        null,
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        null);
  }
}
