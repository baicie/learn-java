package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RcaRepository {
  Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId);

  List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds);

  Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId);

  void saveAnalysis(
      String id,
      String tenantId,
      String incidentId,
      String suspectedRootCause,
      BigDecimal confidence,
      String summary,
      String evidenceJson,
      String modelVersion);

  Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id);

  void updateIncidentRca(
      String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence);

  void addIncidentTimeline(
      String id,
      String incidentId,
      OffsetDateTime eventTime,
      String title,
      String description,
      String payloadJson);
}
