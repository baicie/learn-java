package io.aegisops.workrecord.application.command;

import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.OffsetDateTime;

public record CreateAsyncJobCommand(
    AsyncJobType jobType,
    String requestJson,
    String sourceObjectKey,
    OffsetDateTime expiresAt,
    String outboxJobName) {

  public CreateAsyncJobCommand {
    if (jobType == null) {
      throw new IllegalArgumentException("jobType is required");
    }
    requestJson = requestJson == null || requestJson.isBlank() ? "{}" : requestJson;
    if (outboxJobName == null || outboxJobName.isBlank()) {
      throw new IllegalArgumentException("outboxJobName is required");
    }
  }
}
