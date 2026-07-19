package io.aegisops.ai.model.infrastructure;

import io.aegisops.ai.model.ServerSideAiModelManagement;
import io.aegisops.ai.model.domain.DeepSeekConnectionResult;
import io.aegisops.ai.model.port.DeepSeekConnectionClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ServerSideAiModelManagement
public class HttpDeepSeekConnectionClient implements DeepSeekConnectionClient {
  @Override
  public DeepSeekConnectionResult test(String baseUrl, String apiKey, String modelName) {
    try {
      RestClient.builder()
          .baseUrl(baseUrl)
          .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .build()
          .get()
          .uri("/models")
          .retrieve()
          .toBodilessEntity();
      return new DeepSeekConnectionResult(true, "DeepSeek 连接成功");
    } catch (RestClientResponseException exception) {
      return new DeepSeekConnectionResult(
          false, "DeepSeek 连接失败（HTTP " + exception.getStatusCode().value() + "）");
    } catch (RuntimeException exception) {
      return new DeepSeekConnectionResult(false, "DeepSeek 连接失败，请检查网络和 API 地址");
    }
  }
}
