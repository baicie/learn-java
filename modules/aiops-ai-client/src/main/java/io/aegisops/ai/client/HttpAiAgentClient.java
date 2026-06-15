package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.common.exception.AppException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class HttpAiAgentClient implements AiAgentClient {
  private final AgentClientProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  public HttpAiAgentClient(AgentClientProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, createRestTemplate(properties));
  }

  HttpAiAgentClient(
      AgentClientProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
  }

  @Override
  public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-AegisOps-Internal-Token", properties.normalizedInternalToken());

      HttpEntity<String> entity =
          new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

      ResponseEntity<AgentDiagnosisResponse> response =
          restTemplate.exchange(
              properties.normalizedBaseUrl() + "/v1/diagnose",
              HttpMethod.POST,
              entity,
              AgentDiagnosisResponse.class);

      AgentDiagnosisResponse body = response.getBody();
      if (body == null) {
        throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
      }

      return body;
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("AI_AGENT_CALL_FAILED", "Failed to call AI diagnosis agent");
    }
  }

  private static RestTemplate createRestTemplate(AgentClientProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.normalizedConnectTimeoutMillis());
    factory.setReadTimeout(properties.normalizedReadTimeoutMillis());
    return new RestTemplate(factory);
  }
}
