package io.aegisops.rca;

import java.util.List;

public record RcaAnalysisContext(
    RcaIncidentRecord incident,
    List<RcaAlertRecord> alerts,
    List<RcaAssetRelationRecord> assetRelations,
    List<RcaDiagnosisEvidenceRecord> diagnosisEvidence) {
  public RcaEvidenceIndex evidenceIndex() {
    return RcaEvidenceIndex.from(diagnosisEvidence);
  }
}
