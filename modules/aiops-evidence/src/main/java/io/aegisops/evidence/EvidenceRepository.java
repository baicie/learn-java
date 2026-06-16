package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;

public interface EvidenceRepository {
  LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns);

  ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges);
}
