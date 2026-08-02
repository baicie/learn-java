package io.aegisops.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import io.aegisops.common.security.DiagnosisGrantClaims;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class InternalServiceAuthProbeControllerTest {
  @Test
  void returnsVerifiedServiceIdentityAndDiagnosisContext() throws Exception {
    MockMvc mvc = standaloneSetup(new InternalServiceAuthProbeController()).build();
    InternalServicePrincipal principal =
        new InternalServicePrincipal("svc:aiops-agent", Set.of("evidence:read"));
    DiagnosisGrantClaims grant =
        new DiagnosisGrantClaims(
            "aegisops-app",
            "diagnosis:diagnosis-probe",
            Set.of("aegisops-internal-api", "aiops-agent-api"),
            List.of("evidence:read"),
            "tenant-probe",
            "incident-probe",
            "diagnosis-probe",
            "trace-probe",
            Instant.parse("2026-07-30T08:00:00Z"),
            Instant.parse("2026-07-30T08:05:00Z"),
            "grant-probe");

    mvc.perform(
            post("/internal/agent/auth/probe")
                .requestAttr(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL, principal)
                .requestAttr(SecurityConstants.REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT, grant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.serviceId").value("svc:aiops-agent"))
        .andExpect(jsonPath("$.tenantId").value("tenant-probe"))
        .andExpect(jsonPath("$.incidentId").value("incident-probe"))
        .andExpect(jsonPath("$.traceId").value("trace-probe"));
  }
}
