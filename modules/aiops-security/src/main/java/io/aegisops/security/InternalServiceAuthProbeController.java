package io.aegisops.security;

import io.aegisops.common.security.DiagnosisGrantClaims;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "aiops.internal-agent-api", name = "enabled", havingValue = "true")
public class InternalServiceAuthProbeController {
  @PostMapping("/internal/agent/auth/probe")
  public InternalServiceAuthProbeResponse probe(
      @RequestAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL)
          InternalServicePrincipal principal,
      @RequestAttribute(SecurityConstants.REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT)
          DiagnosisGrantClaims claims) {
    return new InternalServiceAuthProbeResponse(
        true, principal.serviceId(), claims.tenantId(), claims.incidentId(), claims.traceId());
  }
}
