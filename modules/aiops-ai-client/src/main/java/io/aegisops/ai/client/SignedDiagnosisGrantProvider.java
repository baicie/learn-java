package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.agent", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentGrantProperties.class)
public class SignedDiagnosisGrantProvider implements DiagnosisGrantProvider {
  private final AgentGrantProperties properties;
  private final DiagnosisGrantCodec codec;
  private final Clock clock;

  @Autowired
  public SignedDiagnosisGrantProvider(AgentGrantProperties properties, ObjectMapper objectMapper) {
    this(properties, new DiagnosisGrantCodec(objectMapper, Clock.systemUTC()), Clock.systemUTC());
  }

  SignedDiagnosisGrantProvider(
      AgentGrantProperties properties, DiagnosisGrantCodec codec, Clock clock) {
    properties.validate();
    this.properties = properties;
    this.codec = codec;
    this.clock = clock;
  }

  @Override
  public String issue(AgentDiagnosisRequest request) {
    Instant issuedAt = clock.instant();
    return codec.issue(
        properties.getSecret(),
        new DiagnosisGrantClaims(
            properties.getIssuer(),
            properties.getAudience(),
            request.tenantId(),
            request.incidentId(),
            request.traceId(),
            issuedAt,
            issuedAt.plusSeconds(properties.getTtlSeconds())));
  }
}
