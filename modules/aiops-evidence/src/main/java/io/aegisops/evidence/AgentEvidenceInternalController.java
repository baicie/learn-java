package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import org.springframework.web.bind.annotation.PostMapping;
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
  public EvidenceQueryResponse query(@RequestBody EvidenceQueryRequest request) {
    return service.query(request);
  }
}
