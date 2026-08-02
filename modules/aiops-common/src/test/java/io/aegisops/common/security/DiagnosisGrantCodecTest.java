package io.aegisops.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DiagnosisGrantCodecTest {
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
  private static KeyPair signingKeys;
  private static KeyPair otherKeys;

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DiagnosisGrantCodec codec = new DiagnosisGrantCodec(new ObjectMapper(), clock);

  @BeforeAll
  static void generateKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
    signingKeys = generator.generateKeyPair();
    otherKeys = generator.generateKeyPair();
  }

  @Test
  void issuesAndVerifiesIncidentBoundEd25519GrantForBothAudiences() {
    String token = codec.issue(signingKeys.getPrivate(), "diagnosis-2026-08", claims());

    DiagnosisGrantClaims agentClaims =
        codec.verify(
            Map.of("diagnosis-2026-08", signingKeys.getPublic()), token, "aiops-agent-api");
    DiagnosisGrantClaims internalApiClaims =
        codec.verify(
            Map.of("diagnosis-2026-08", signingKeys.getPublic()), token, "aegisops-internal-api");

    assertThat(agentClaims).isEqualTo(internalApiClaims);
    assertThat(agentClaims.issuer()).isEqualTo("aegisops-control-plane");
    assertThat(agentClaims.subject()).isEqualTo("diagnosis:diag_1");
    assertThat(agentClaims.audiences())
        .containsExactlyInAnyOrder("aiops-agent-api", "aegisops-internal-api");
    assertThat(agentClaims.scopes()).containsExactly("diagnosis:execute", "evidence:read");
    assertThat(agentClaims.tenantId()).isEqualTo("tenant_1");
    assertThat(agentClaims.incidentId()).isEqualTo("inc_1");
    assertThat(agentClaims.diagnosisId()).isEqualTo("diag_1");
    assertThat(agentClaims.traceId()).isEqualTo("trace_1");
    assertThat(agentClaims.jti()).isEqualTo("grant_1");
  }

  @Test
  void rejectsWrongKeyUnknownKeyIdAndTamperedPayload() {
    String token = codec.issue(signingKeys.getPrivate(), "diagnosis-2026-08", claims());

    assertThatThrownBy(
            () ->
                codec.verify(
                    Map.of("diagnosis-2026-08", otherKeys.getPublic()), token, "aiops-agent-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class)
        .hasMessageContaining("signature");
    assertThatThrownBy(
            () ->
                codec.verify(
                    Map.of("other-kid", signingKeys.getPublic()), token, "aiops-agent-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class)
        .hasMessageContaining("key id");

    String[] segments = token.split("\\.");
    byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
    payload[payload.length - 1] ^= 1;
    String tampered =
        segments[0]
            + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
            + "."
            + segments[2];
    assertThatThrownBy(
            () ->
                codec.verify(
                    Map.of("diagnosis-2026-08", signingKeys.getPublic()),
                    tampered,
                    "aiops-agent-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
  }

  @Test
  void rejectsExpiredWrongAudienceAndLongLivedGrant() {
    DiagnosisGrantClaims expired =
        new DiagnosisGrantClaims(
            "aegisops-control-plane",
            "diagnosis:diag_1",
            Set.of("aiops-agent-api", "aegisops-internal-api"),
            List.of("diagnosis:execute"),
            "tenant_1",
            "inc_1",
            "diag_1",
            "trace_1",
            NOW.minusSeconds(600),
            NOW.minusSeconds(300),
            "grant_1");
    String expiredToken = codec.issue(signingKeys.getPrivate(), "diagnosis-2026-08", expired);

    assertThatThrownBy(
            () ->
                codec.verify(
                    Map.of("diagnosis-2026-08", signingKeys.getPublic()),
                    expiredToken,
                    "aiops-agent-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class)
        .hasMessageContaining("expired");
    String validToken = codec.issue(signingKeys.getPrivate(), "diagnosis-2026-08", claims());
    assertThatThrownBy(
            () ->
                codec.verify(
                    Map.of("diagnosis-2026-08", signingKeys.getPublic()),
                    validToken,
                    "different-audience"))
        .isInstanceOf(InvalidDiagnosisGrantException.class)
        .hasMessageContaining("audience");

    DiagnosisGrantClaims tooLong =
        new DiagnosisGrantClaims(
            "aegisops-control-plane",
            "diagnosis:diag_1",
            Set.of("aiops-agent-api"),
            List.of("diagnosis:execute"),
            "tenant_1",
            "inc_1",
            "diag_1",
            "trace_1",
            NOW,
            NOW.plusSeconds(301),
            "grant_1");
    assertThatThrownBy(() -> codec.issue(signingKeys.getPrivate(), "diagnosis-2026-08", tooLong))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
  }

  @Test
  void loadsPkcs8PrivateAndX509PublicPem() {
    String privatePem = pem("PRIVATE KEY", signingKeys.getPrivate().getEncoded());
    String publicPem = pem("PUBLIC KEY", signingKeys.getPublic().getEncoded());

    assertThat(Ed25519KeyLoader.loadPrivateKey(privatePem).getEncoded())
        .isEqualTo(signingKeys.getPrivate().getEncoded());
    assertThat(Ed25519KeyLoader.loadPublicKey(publicPem).getEncoded())
        .isEqualTo(signingKeys.getPublic().getEncoded());
  }

  private DiagnosisGrantClaims claims() {
    return new DiagnosisGrantClaims(
        "aegisops-control-plane",
        "diagnosis:diag_1",
        Set.of("aiops-agent-api", "aegisops-internal-api"),
        List.of("diagnosis:execute", "evidence:read"),
        "tenant_1",
        "inc_1",
        "diag_1",
        "trace_1",
        NOW,
        NOW.plusSeconds(300),
        "grant_1");
  }

  private static String pem(String type, byte[] encoded) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
        + "\n-----END "
        + type
        + "-----\n";
  }
}
