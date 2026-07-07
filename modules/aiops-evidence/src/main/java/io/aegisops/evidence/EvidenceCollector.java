package io.aegisops.evidence;

/** Strategy interface for collecting evidence from a specific source. */
public interface EvidenceCollector {
  String collectorKey();

  boolean supports(EvidenceCollectRequest request);

  EvidenceCollectResponse collect(
      String tenantId, String incidentId, EvidenceCollectRequest request);
}
