package io.aegisops.ai.client.workrecord;

public interface WorkRecordAiClient {
  WorkRecordGenerationResponse generate(WorkRecordGenerationRequest request);
}
