package io.aegisops.ai.client.workrecord;

import io.aegisops.ai.client.AgentClientProperties;
import io.aegisops.ai.client.AgentContract;
import io.aegisops.ai.client.AgentCredentialProvider;
import io.aegisops.common.exception.AppException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWorkRecordAiClient implements WorkRecordAiClient {
  private final AgentClientProperties properties;
  private final RestClient restClient;
  private final AgentCredentialProvider credentialProvider;

  public HttpWorkRecordAiClient(
      AgentClientProperties properties,
      RestClient.Builder builder,
      AgentCredentialProvider credentialProvider) {
    this.properties = properties;
    this.credentialProvider = credentialProvider;
    this.restClient =
        builder
            .baseUrl(properties.normalizedBaseUrl())
            .requestFactory(createRequestFactory(properties))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  static SimpleClientHttpRequestFactory createRequestFactory(AgentClientProperties properties) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.normalizedConnectTimeoutMillis());
    factory.setReadTimeout(properties.normalizedReadTimeoutMillis());
    return factory;
  }

  @Override
  public WorkRecordGenerationResponse generate(WorkRecordGenerationRequest request) {
    try {
      WorkRecordGenerationResponse response =
          restClient
              .post()
              .uri("/v1/work-record/generate")
              .headers(credentialProvider::apply)
              .header(AgentContract.TRACE_ID_HEADER, request.traceId())
              .body(request)
              .retrieve()
              .body(WorkRecordGenerationResponse.class);
      if (response == null || response.markdown() == null || response.markdown().isBlank()) {
        throw new AppException("AI_WORK_RECORD_EMPTY", "AI returned an empty work-record result");
      }
      return response;
    } catch (AppException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AppException("AI_WORK_RECORD_CALL_FAILED", "Failed to call work-record AI agent");
    }
  }
}
