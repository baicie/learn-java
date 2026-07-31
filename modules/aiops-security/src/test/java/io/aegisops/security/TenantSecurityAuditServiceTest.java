package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TenantSecurityAuditServiceTest {
  @Test
  void recordsVerifiedInternalServiceAsAuditActor() {
    AtomicReference<TenantSecurityEventCreateCommand> captured = new AtomicReference<>();
    TenantSecurityAuditService auditService =
        new TenantSecurityAuditService(captured::set, new ObjectMapper());
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.setAttribute(
        SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL,
        new InternalServicePrincipal("svc:aiops-agent", Set.of("evidence:read")));

    auditService.record(
        null, "internal_auth_forbidden", "critical", "Missing endpoint scope", request);

    assertThat(captured.get().actor()).isEqualTo("svc:aiops-agent");
  }
}
