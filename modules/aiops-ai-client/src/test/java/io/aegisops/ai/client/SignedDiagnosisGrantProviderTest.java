package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignedDiagnosisGrantProviderTest {
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
  private static KeyPair signingKeys;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DiagnosisGrantCodec codec = new DiagnosisGrantCodec(new ObjectMapper(), clock);

  @BeforeAll
  static void generateKeys() throws Exception {
    signingKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  @Test
  void defaultsGrantIssuerToControlPlaneIdentity() {
    AgentGrantProperties properties = new AgentGrantProperties();

    assertThat(properties.getIssuer()).isEqualTo("aegisops-app");
    assertThat(properties.getScopes()).containsExactly("diagnosis:execute", "diagnosis:resume");
  }

  @Test
  void mapsDiagnosisRequestIdentityScopesAndConfiguredTtlIntoSignedGrant() {
    AgentGrantProperties properties = propertiesWithTtl(300);
    SignedDiagnosisGrantProvider provider =
        new SignedDiagnosisGrantProvider(properties, codec, clock, signingKeys.getPrivate());

    String token = provider.issue(request("tenant_1", "inc_1", "diag_1", "trace_1"));

    DiagnosisGrantClaims claims =
        codec.verify(
            Map.of(properties.getKeyId(), signingKeys.getPublic()), token, "aiops-agent-api");
    assertThat(claims.issuer()).isEqualTo("aegisops-app");
    assertThat(claims.subject()).isEqualTo("diagnosis:diag_1");
    assertThat(claims.audiences())
        .containsExactlyInAnyOrder("aiops-agent-api", "aegisops-internal-api");
    assertThat(claims.scopes()).containsExactly("diagnosis:execute", "diagnosis:resume");
    assertThat(claims.tenantId()).isEqualTo("tenant_1");
    assertThat(claims.incidentId()).isEqualTo("inc_1");
    assertThat(claims.diagnosisId()).isEqualTo("diag_1");
    assertThat(claims.traceId()).isEqualTo("trace_1");
    assertThat(claims.issuedAt()).isEqualTo(NOW);
    assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    assertThat(Duration.between(claims.issuedAt(), claims.expiresAt()))
        .isEqualTo(Duration.ofSeconds(300));
  }

  @Test
  void rejectsIncompleteGrantMetadataAndTtlOutsideBoundary() {
    AgentGrantProperties tooLong = propertiesWithTtl(301);
    assertThatThrownBy(
            () -> new SignedDiagnosisGrantProvider(tooLong, codec, clock, signingKeys.getPrivate()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ttl-seconds");

    AgentGrantProperties blankIssuer = propertiesWithTtl(300);
    blankIssuer.setIssuer(" ");
    assertThatThrownBy(
            () ->
                new SignedDiagnosisGrantProvider(
                    blankIssuer, codec, clock, signingKeys.getPrivate()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("issuer");

    AgentGrantProperties blankAudience = propertiesWithTtl(300);
    blankAudience.setAudiences(List.of(" "));
    assertThatThrownBy(
            () ->
                new SignedDiagnosisGrantProvider(
                    blankAudience, codec, clock, signingKeys.getPrivate()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiences");

    AgentGrantProperties blankScope = propertiesWithTtl(300);
    blankScope.setScopes(List.of("diagnosis:execute", " "));
    assertThatThrownBy(
            () ->
                new SignedDiagnosisGrantProvider(
                    blankScope, codec, clock, signingKeys.getPrivate()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("scopes");
  }

  private AgentGrantProperties propertiesWithTtl(long ttlSeconds) {
    AgentGrantProperties properties = new AgentGrantProperties();
    properties.setIssuer("aegisops-app");
    properties.setAudiences(List.of("aiops-agent-api", "aegisops-internal-api"));
    properties.setScopes(List.of("diagnosis:execute", "diagnosis:resume"));
    properties.setKeyId("task-grant-v1");
    properties.setTtlSeconds(ttlSeconds);
    return properties;
  }

  private AgentDiagnosisRequest request(
      String tenantId, String incidentId, String diagnosisId, String traceId) {
    return new AgentDiagnosisRequest(
        "v1",
        tenantId,
        incidentId,
        null,
        List.of(),
        null,
        List.of(),
        List.of(),
        "zh-CN",
        traceId,
        diagnosisId);
  }
}
