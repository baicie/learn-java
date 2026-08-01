package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignedDiagnosisGrantProviderTest {
  private static final String SECRET = "test-diagnosis-grant-secret-with-32-bytes";
  private static final String AUDIENCE = "aegisops-internal-api";
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DiagnosisGrantCodec codec = new DiagnosisGrantCodec(new ObjectMapper(), clock);

  @Test
  void defaultsGrantIssuerToServerIdentity() {
    assertThat(new AgentGrantProperties().getIssuer()).isEqualTo("aiops-server");
  }

  @Test
  void mapsDiagnosisRequestIdentityAndConfiguredTtlIntoSignedGrant() {
    AgentGrantProperties properties = propertiesWithTtl(300);
    SignedDiagnosisGrantProvider provider =
        new SignedDiagnosisGrantProvider(properties, codec, clock);

    String token = provider.issue(request("tenant_1", "inc_1", "trace_1"));

    DiagnosisGrantClaims claims = codec.verify(SECRET, token, AUDIENCE);
    assertThat(claims.tenantId()).isEqualTo("tenant_1");
    assertThat(claims.incidentId()).isEqualTo("inc_1");
    assertThat(claims.traceId()).isEqualTo("trace_1");
    assertThat(claims.issuedAt()).isEqualTo(NOW);
    assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    assertThat(Duration.between(claims.issuedAt(), claims.expiresAt()))
        .isEqualTo(Duration.ofSeconds(300));
  }

  @Test
  void rejectsConfiguredTtlOutsideGrantBoundaryAtStartup() {
    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(propertiesWithTtl(301), codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-seconds");

    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(propertiesWithTtl(0), codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-seconds");
  }

  @Test
  void rejectsWeakOrIncompleteGrantConfigurationAtStartup() {
    AgentGrantProperties weakSecret = propertiesWithTtl(300);
    weakSecret.setSecret("x".repeat(31));
    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(weakSecret, codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("secret");

    AgentGrantProperties blankSecret = propertiesWithTtl(300);
    blankSecret.setSecret(" ".repeat(32));
    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(blankSecret, codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("secret");

    AgentGrantProperties blankIssuer = propertiesWithTtl(300);
    blankIssuer.setIssuer(" ");
    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(blankIssuer, codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("issuer");

    AgentGrantProperties blankAudience = propertiesWithTtl(300);
    blankAudience.setAudience("");
    assertThatThrownBy(() -> new SignedDiagnosisGrantProvider(blankAudience, codec, clock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void acceptsGrantSecretBasedOnUtf8BytesAndMinimumTtl() {
    AgentGrantProperties properties = propertiesWithTtl(1);
    properties.setSecret("中".repeat(11));

    SignedDiagnosisGrantProvider provider =
        new SignedDiagnosisGrantProvider(properties, codec, clock);

    String token = provider.issue(request("tenant_1", "inc_1", "trace_1"));
    DiagnosisGrantClaims claims = codec.verify(properties.getSecret(), token, AUDIENCE);
    assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(1));
  }

  private AgentGrantProperties propertiesWithTtl(long ttlSeconds) {
    AgentGrantProperties properties = new AgentGrantProperties();
    properties.setIssuer("aiops-worker");
    properties.setAudience(AUDIENCE);
    properties.setSecret(SECRET);
    properties.setTtlSeconds(ttlSeconds);
    return properties;
  }

  private AgentDiagnosisRequest request(String tenantId, String incidentId, String traceId) {
    return new AgentDiagnosisRequest(
        "v1", tenantId, incidentId, null, List.of(), null, List.of(), List.of(), "zh-CN", traceId);
  }
}
