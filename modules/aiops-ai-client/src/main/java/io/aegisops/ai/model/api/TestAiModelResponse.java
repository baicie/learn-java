package io.aegisops.ai.model.api;

import java.time.OffsetDateTime;

public record TestAiModelResponse(boolean success, String message, OffsetDateTime testedAt) {}
