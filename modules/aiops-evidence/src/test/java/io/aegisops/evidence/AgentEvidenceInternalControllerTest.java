package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.MetricEvidence;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AgentEvidenceInternalControllerTest {
  @Test
  void acceptsRequestAuthorizedByDiagnosisGrant() throws Exception {
    MockMvc mvc = standaloneSetup(controller()).build();

    mvc.perform(
            post("/internal/agent/evidence/query")
                .requestAttr(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE, grant())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request())))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsRequestForDifferentDiagnosisContext() {
    EvidenceQueryRequest forged =
        new EvidenceQueryRequest(
            "agent-diagnosis.v1",
            "tenant_2",
            "inc_2",
            "trace_2",
            "asset_1",
            null,
            null,
            List.of(),
            List.of(),
            List.of());

    assertThrows(SecurityException.class, () -> controller().query(forged, grant()));
  }

  private AgentEvidenceInternalController controller() {
    AgentEvidenceProperties properties = new AgentEvidenceProperties(60, 10, 10);
    AgentEvidenceService service =
        new AgentEvidenceService(
            properties,
            req -> MetricEvidence.unavailable("noop"),
            new EvidenceRepository() {
              @Override
              public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
                return LogEvidence.unavailable("noop");
              }

              @Override
              public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
                return ChangeEvidence.unavailable("noop");
              }
            });

    return new AgentEvidenceInternalController(service);
  }

  private EvidenceQueryRequest request() {
    return new EvidenceQueryRequest(
        "agent-diagnosis.v1",
        "tenant_1",
        "inc_1",
        "trace_1",
        "asset_1",
        OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
        OffsetDateTime.parse("2026-06-16T10:10:00+09:00"),
        List.of("fp_cpu"),
        List.of("CPU high"),
        List.of("checkout-service"));
  }

  private DiagnosisGrantClaims grant() {
    return new DiagnosisGrantClaims(
        "aiops-server",
        "aegisops-internal-api",
        "tenant_1",
        "inc_1",
        "trace_1",
        Instant.parse("2026-07-30T08:00:00Z"),
        Instant.parse("2026-07-30T08:05:00Z"));
  }

  private String json(Object value) throws Exception {
    return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(value);
  }
}
