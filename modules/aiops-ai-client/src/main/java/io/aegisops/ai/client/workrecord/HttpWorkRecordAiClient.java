package io.aegisops.ai.client.workrecord;

import io.aegisops.ai.client.AgentClientProperties;
import io.aegisops.ai.client.AgentContract;
import io.aegisops.ai.client.AgentMtlsRequestFactory;
import io.aegisops.common.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "aiops.agent", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentClientProperties.class)
public class HttpWorkRecordAiClient implements WorkRecordAiClient {
  private static final Logger log = LoggerFactory.getLogger(HttpWorkRecordAiClient.class);

  private final AgentClientProperties properties;
  private final RestClient restClient;

  @Autowired
  public HttpWorkRecordAiClient(
      AgentClientProperties properties, RestClient.Builder builder, SslBundles sslBundles) {
    this.properties = properties;
    this.restClient =
        builder
            .baseUrl(properties.normalizedBaseUrl())
            .requestFactory(AgentMtlsRequestFactory.create(properties, sslBundles))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  HttpWorkRecordAiClient(AgentClientProperties properties, RestClient.Builder builder) {
    this.properties = properties;
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
      log.error(
          "Work-record AI agent call failed (traceId={}): type={} message={}",
          request.traceId(),
          ex.getClass().getSimpleName(),
          ex.getMessage(),
          ex);
      String detail =
          "Failed to call work-record AI agent: "
              + ex.getClass().getSimpleName()
              + " — "
              + (ex.getMessage() == null ? "(no message)" : ex.getMessage());
      throw new AppException("AI_WORK_RECORD_CALL_FAILED", detail);
    }
  }
}
