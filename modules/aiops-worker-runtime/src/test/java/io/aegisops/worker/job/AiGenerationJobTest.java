package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.workrecord.application.service.AiGenerationProcessor;
import org.junit.jupiter.api.Test;

class AiGenerationJobTest {
  @Test
  void tellsProcessorWhetherCurrentDeliveryIsFinalAttempt() {
    AiGenerationProcessor processor = mock(AiGenerationProcessor.class);
    AiGenerationJob job = new AiGenerationJob(processor, new ObjectMapper());
    AutomationOutboxRecord retryable = row(1, 3);
    AutomationOutboxRecord finalAttempt = row(2, 3);

    assertThat(job.handle(retryable).isSuccess()).isTrue();
    assertThat(job.handle(finalAttempt).isSuccess()).isTrue();

    verify(processor).process("tenant-1", "ai-1", false);
    verify(processor).process("tenant-1", "ai-1", true);
  }

  private static AutomationOutboxRecord row(int retryCount, int maxRetries) {
    return new AutomationOutboxRecord()
        .setTenantId("tenant-1")
        .setPayload(org.jooq.JSONB.valueOf("{\"tenantId\":\"tenant-1\",\"generationId\":\"ai-1\"}"))
        .setRetryCount(retryCount)
        .setMaxRetries(maxRetries);
  }
}
