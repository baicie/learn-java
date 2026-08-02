package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.common.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@ConditionalOnProperty(prefix = "aiops.agent", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentClientProperties.class)
public class HttpAiAgentClient implements AiAgentClient {
  private final AgentClientProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final AgentContractValidator contractValidator;
  private final DiagnosisGrantProvider diagnosisGrantProvider;

  @Autowired
  public HttpAiAgentClient(
      AgentClientProperties properties,
      ObjectMapper objectMapper,
      DiagnosisGrantProvider diagnosisGrantProvider,
      SslBundles sslBundles) {
    this(
        properties,
        objectMapper,
        new RestTemplate(AgentMtlsRequestFactory.create(properties, sslBundles)),
        new AgentContractValidator(),
        diagnosisGrantProvider);
  }

  HttpAiAgentClient(
      AgentClientProperties properties,
      ObjectMapper objectMapper,
      RestTemplate restTemplate,
      AgentContractValidator contractValidator,
      DiagnosisGrantProvider diagnosisGrantProvider) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
    this.contractValidator = contractValidator;
    this.diagnosisGrantProvider = diagnosisGrantProvider;
  }

  @Override
  public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
    try {
      contractValidator.validateRequest(request);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set(AgentContract.TRACE_ID_HEADER, request.traceId());
      headers.set(AgentContract.CONTRACT_VERSION_HEADER, AgentContract.DIAGNOSIS_CONTRACT_VERSION);
      headers.set(AgentContract.DIAGNOSIS_GRANT_HEADER, diagnosisGrantProvider.issue(request));

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
}
