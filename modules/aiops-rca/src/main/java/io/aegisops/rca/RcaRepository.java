package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RcaRepository {
  Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId);

  List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds);

  Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId);

  void saveAnalysis(SaveAnalysisParams params);

  Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id);

  void updateIncidentRca(
      String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence);

  void addIncidentTimeline(AddTimelineParams params);
}
