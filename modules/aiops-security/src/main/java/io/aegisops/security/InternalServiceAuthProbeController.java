package io.aegisops.security;

import io.aegisops.common.security.DiagnosisGrantClaims;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
