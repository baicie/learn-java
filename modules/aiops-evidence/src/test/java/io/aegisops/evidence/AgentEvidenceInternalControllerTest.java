package io.aegisops.evidence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.MetricEvidence;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AgentEvidenceInternalControllerTest {
  @Test
  void rejectsInvalidToken() throws Exception {
    MockMvc mvc = standaloneSetup(controller()).build();

    mvc.perform(
            post("/internal/agent/evidence/query")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-AegisOps-Internal-Token", "bad")
                .content(json(request())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void acceptsValidToken() throws Exception {
    MockMvc mvc = standaloneSetup(controller()).build();

    mvc.perform(
            post("/internal/agent/evidence/query")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-AegisOps-Internal-Token", "token")
                .content(json(request())))
        .andExpect(status().isOk());
  }

  private AgentEvidenceInternalController controller() {
    AgentEvidenceProperties properties = new AgentEvidenceProperties("token", 60, 10, 10);
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

    return new AgentEvidenceInternalController(properties, service);
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
        List.of("CPU high"));
  }

  private String json(Object value) throws Exception {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .writeValueAsString(value);
  }
}
