package io.aegisops.ai.model.port;

import io.aegisops.ai.model.domain.DeepSeekConnectionResult;

public interface DeepSeekConnectionClient {
  DeepSeekConnectionResult test(String baseUrl, String apiKey, String modelName);
}
