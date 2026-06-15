package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAiAgentClientTest {
  @Test
  void postsDiagnosisRequestToAgent() {
    ObjectMapper objectMapper = new ObjectMapper();
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    AgentClientProperties properties =
        new AgentClientProperties("http://agent:9008", "test-token", 1000, 1000);

    HttpAiAgentClient client = new HttpAiAgentClient(properties, objectMapper, restTemplate);

    server
        .expect(requestTo("http://agent:9008/v1/diagnose"))
        .andExpect(header("X-AegisOps-Internal-Token", "test-token"))
        .andRespond(
            withSuccess(
                """
                        {
                          "provider": "aiops-agent",
                          "model": "langgraph-deterministic",
                          "agentName": "aegisops_diagnosis_graph",
                          "summary": "summary",
                          "rootCause": "root",
                          "impact": "impact",
                          "nextSteps": ["step"],
                          "runbookSuggestions": ["runbook"],
                          "risks": ["risk"],
                          "raw": {"ok": true}
                        }
                        """,
                MediaType.APPLICATION_JSON));

    AgentDiagnosisResponse response =
        client.diagnose(
            new AgentDiagnosisRequest(
                "tenant_1", "inc_1", null, List.of(), null, "zh-CN", "trace_1"));

    assertEquals("aiops-agent", response.provider());
    assertEquals("aegisops_diagnosis_graph", response.agentName());
    assertEquals("root", response.rootCause());

    server.verify();
  }
}
