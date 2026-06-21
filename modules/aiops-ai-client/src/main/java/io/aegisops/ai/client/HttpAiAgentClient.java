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
  private final AgentContractValidator contractValidator;

  public HttpAiAgentClient(AgentClientProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, createRestTemplate(properties), new AgentContractValidator());
  }

  HttpAiAgentClient(
      AgentClientProperties properties,
      ObjectMapper objectMapper,
      RestTemplate restTemplate,
      AgentContractValidator contractValidator) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
    this.contractValidator = contractValidator;
  }

  @Override
  public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
    try {
      contractValidator.validateRequest(request);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set(AgentContract.INTERNAL_TOKEN_HEADER, properties.normalizedInternalToken());
      headers.set(AgentContract.TRACE_ID_HEADER, request.traceId());
      headers.set(AgentContract.CONTRACT_VERSION_HEADER, AgentContract.DIAGNOSIS_CONTRACT_VERSION);

      HttpEntity<String> entity =
          new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

      ResponseEntity<AgentDiagnosisResponse> response =
          restTemplate.exchange(
              properties.normalizedBaseUrl() + "/v1/diagnose",
              HttpMethod.POST,
              entity,
              AgentDiagnosisResponse.class);

      AgentDiagnosisResponse body = response.getBody();
      contractValidator.validateResponse(body);

      return body;
    } catch (AgentContractViolationException ex) {
      throw ex;
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
