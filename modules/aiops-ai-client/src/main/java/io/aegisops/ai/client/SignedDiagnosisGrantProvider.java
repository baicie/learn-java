package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import io.aegisops.common.security.Ed25519KeyLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
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
  private final PrivateKey privateKey;

  @Autowired
  public SignedDiagnosisGrantProvider(AgentGrantProperties properties, ObjectMapper objectMapper) {
    this(
        properties,
        new DiagnosisGrantCodec(objectMapper, Clock.systemUTC()),
        Clock.systemUTC(),
        loadPrivateKey(properties));
  }

  SignedDiagnosisGrantProvider(
      AgentGrantProperties properties,
      DiagnosisGrantCodec codec,
      Clock clock,
      PrivateKey privateKey) {
    properties.validateMetadata();
    this.properties = properties;
    this.codec = codec;
    this.clock = clock;
    this.privateKey = privateKey;
  }

  @Override
  public String issue(AgentDiagnosisRequest request) {
    Instant issuedAt = clock.instant();
    return codec.issue(
        privateKey,
        properties.getKeyId(),
        new DiagnosisGrantClaims(
            properties.getIssuer(),
            "diagnosis:" + request.diagnosisId(),
            Set.copyOf(properties.getAudiences()),
            properties.getScopes(),
            request.tenantId(),
            request.incidentId(),
            request.diagnosisId(),
            request.traceId(),
            issuedAt,
            issuedAt.plusSeconds(properties.getTtlSeconds()),
            UUID.randomUUID().toString()));
  }

  private static PrivateKey loadPrivateKey(AgentGrantProperties properties) {
    properties.validate();
    try {
      return Ed25519KeyLoader.loadPrivateKey(
          Files.readString(Path.of(properties.getPrivateKeyFile())));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read diagnosis grant private key", exception);
    }
  }
}
