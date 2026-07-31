package io.aegisops.evidence;

import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/evidence")
public class AgentEvidenceInternalController {
  private final AgentEvidenceService service;

  public AgentEvidenceInternalController(AgentEvidenceService service) {
    this.service = service;
  }

  @PostMapping("/query")
  public EvidenceQueryResponse query(
      @RequestBody EvidenceQueryRequest request,
      @RequestAttribute(DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE)
          DiagnosisGrantClaims claims) {
    DiagnosisGrantAuthorization.requireContext(
        claims, request.tenantId(), request.incidentId(), request.traceId());
    return service.query(request);
  }
}
