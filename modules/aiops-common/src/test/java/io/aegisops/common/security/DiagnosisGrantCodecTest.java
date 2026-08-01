package io.aegisops.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DiagnosisGrantCodecTest {
  private static final String SECRET = "test-diagnosis-grant-secret-with-32-bytes";
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DiagnosisGrantCodec codec = new DiagnosisGrantCodec(new ObjectMapper(), clock);

  @Test
  void issuesAndVerifiesIncidentBoundGrant() {
    String token =
        codec.issue(
            SECRET,
            new DiagnosisGrantClaims(
                "aiops-server",
                "aegisops-internal-api",
                "tenant_1",
                "inc_1",
                "trace_1",
                NOW,
                NOW.plusSeconds(300)));

    DiagnosisGrantClaims claims = codec.verify(SECRET, token, "aegisops-internal-api");

    assertThat(claims.issuer()).isEqualTo("aiops-server");
    assertThat(claims.tenantId()).isEqualTo("tenant_1");
    assertThat(claims.incidentId()).isEqualTo("inc_1");
    assertThat(claims.traceId()).isEqualTo("trace_1");
  }

  @Test
  void rejectsTamperedGrant() {
    String token =
        codec.issue(
            SECRET,
            new DiagnosisGrantClaims(
                "aiops-worker",
                "aegisops-internal-api",
                "tenant_1",
                "inc_1",
                "trace_1",
                NOW,
                NOW.plusSeconds(300)));

    String[] segments = token.split("\\.");
    String signature = segments[2];
    char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
    String tampered = segments[0] + "." + segments[1] + "." + replacement + signature.substring(1);

    assertThatThrownBy(() -> codec.verify(SECRET, tampered, "aegisops-internal-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
  }

  @Test
  void rejectsExpiredOrWrongAudienceGrant() {
    String token =
        codec.issue(
            SECRET,
            new DiagnosisGrantClaims(
                "aiops-server",
                "aegisops-internal-api",
                "tenant_1",
                "inc_1",
                "trace_1",
                NOW.minusSeconds(600),
                NOW.minusSeconds(300)));

    assertThatThrownBy(() -> codec.verify(SECRET, token, "aegisops-internal-api"))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
    assertThatThrownBy(() -> codec.verify(SECRET, token, "different-audience"))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
  }

  @Test
  void rejectsGrantLongerThanFiveMinutes() {
    DiagnosisGrantClaims claims =
        new DiagnosisGrantClaims(
            "aiops-server",
            "aegisops-internal-api",
            "tenant_1",
            "inc_1",
            "trace_1",
            NOW,
            NOW.plusSeconds(301));

    assertThatThrownBy(() -> codec.issue(SECRET, claims))
        .isInstanceOf(InvalidDiagnosisGrantException.class);
  }
}
