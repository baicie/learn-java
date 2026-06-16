package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/agent/evidence")
@EnableConfigurationProperties(AgentEvidenceProperties.class)
public class AgentEvidenceInternalController {
  private final AgentEvidenceProperties properties;
  private final AgentEvidenceService service;

  public AgentEvidenceInternalController(
      AgentEvidenceProperties properties, AgentEvidenceService service) {
    this.properties = properties;
    this.service = service;
  }

  @PostMapping("/query")
  public EvidenceQueryResponse query(
      @RequestHeader(value = "X-AegisOps-Internal-Token", required = false) String token,
      @RequestBody EvidenceQueryRequest request) {
    if (!properties.normalizedInternalToken().equals(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
    }

    return service.query(request);
  }
}
