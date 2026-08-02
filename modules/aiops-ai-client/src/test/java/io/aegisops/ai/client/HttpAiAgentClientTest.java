package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAiAgentClientTest {
  @Test
  void postsDiagnosisRequestWithContractHeaders() {
    ObjectMapper objectMapper = new ObjectMapper();
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    AgentClientProperties properties = new AgentClientProperties("http://agent:9008", 1000, 1000);

    HttpAiAgentClient client =
        new HttpAiAgentClient(
            properties,
            objectMapper,
            restTemplate,
            new AgentContractValidator(),
            request -> "test-diagnosis-grant");

    server
        .expect(requestTo("http://agent:9008/v1/diagnose"))
        .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
        .andExpect(header("X-AegisOps-Diagnosis-Grant", "test-diagnosis-grant"))
        .andExpect(header(AgentContract.TRACE_ID_HEADER, "trace_1"))
        .andExpect(
            header(AgentContract.CONTRACT_VERSION_HEADER, AgentContract.DIAGNOSIS_CONTRACT_VERSION))
        .andRespond(
            withSuccess(
                """
                        {
                          "contractVersion": "agent-diagnosis.v1",
                          "incidentId": "inc_1",
                          "status": "completed",
                          "provider": "aiops-agent",
                          "model": "langgraph-deterministic",
                          "agentName": "aegisops_diagnosis_graph",
                          "summary": "summary",
                          "rootCause": "root",
                          "impact": "impact",
                          "nextSteps": ["step"],
                          "runbookSuggestions": ["runbook"],
                          "risks": ["risk"],
                          "matchedRules": [],
                          "evidenceRefs": [],
                          "timeline": [],
                          "raw": {"ok": true}
                        }
                        """,
                MediaType.APPLICATION_JSON));

    AgentDiagnosisResponse response =
        client.diagnose(
            new AgentDiagnosisRequest(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "tenant_1",
                "inc_1",
                new AgentIncidentContext(
                    "inc_1", null, null, null, null, null, null, null, 0, null, null, null, null,
                    null),
                List.of(),
                null,
                List.of(),
                List.of(),
                "zh-CN",
                "trace_1",
                "diag_1"));

    assertEquals("aiops-agent", response.provider());
    assertEquals("aegisops_diagnosis_graph", response.agentName());
    assertEquals("root", response.rootCause());
    assertEquals(AgentContract.DIAGNOSIS_CONTRACT_VERSION, response.contractVersion());

    server.verify();
  }
}
