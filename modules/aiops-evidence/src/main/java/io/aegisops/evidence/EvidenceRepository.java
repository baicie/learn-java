package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.MultiSourceEvidence;

public interface EvidenceRepository {
  LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns);

  ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges);

  default MultiSourceEvidence queryMultiSource(EvidenceQueryRequest request, int maxItems) {
    return MultiSourceEvidence.unavailable("Multi-source evidence is not enabled.");
  }
}
