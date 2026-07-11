package io.aegisops.rca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RcaService {
  private static final String MODEL_VERSION = "rules-v2-evidence";

  private final RcaRepository repository;
  private final RcaEngine engine;
  private final ObjectMapper objectMapper;

  public RcaService(RcaRepository repository, RcaEngine engine, ObjectMapper objectMapper) {
    this.repository = repository;
    this.engine = engine;
    this.objectMapper = objectMapper;
  }

  public RcaAnalysisResponse latest(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);

    return repository
        .findLatestAnalysis(tenantId, incidentId)
        .map(record -> toResponse(record))
        .orElseThrow(() -> new AppException("RCA_NOT_FOUND", "RCA analysis not found"));
  }

  @Transactional
  public RcaAnalysisResponse analyze(
      String tenantId, String incidentId, RcaAnalyzeRequest request) {
    RcaAnalyzeRequest normalizedRequest = request == null ? new RcaAnalyzeRequest(false) : request;

    if (!normalizedRequest.forceEnabled()) {
      var latest = repository.findLatestAnalysis(tenantId, incidentId);
      if (latest.isPresent()) {
        return toResponse(latest.get());
      }
    }

    RcaIncidentRecord incident =
        repository
            .findIncident(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    List<RcaAlertRecord> alerts = repository.listIncidentAlerts(tenantId, incidentId);
    List<String> assetIds =
        alerts.stream()
            .map(alert -> alert.assetId())
            .filter(item -> item != null)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();

    List<RcaAssetRelationRecord> relations = repository.listAssetRelations(tenantId, assetIds);
    List<RcaDiagnosisEvidenceRecord> diagnosisEvidence =
        repository.listDiagnosisEvidence(tenantId, incidentId);

    RcaAnalysisResult result =
        engine.analyze(new RcaAnalysisContext(incident, alerts, relations, diagnosisEvidence));

    String id = newId("rca");
    String evidenceJson = writeJson(result.evidence());

    repository.saveAnalysis(
        new SaveAnalysisParams(
            id,
            tenantId,
            incidentId,
            result.suspectedRootCause(),
            clampConfidence(result.confidence()),
            result.summary(),
            evidenceJson,
            MODEL_VERSION));

    repository.updateIncidentRca(
        tenantId, incidentId, result.suspectedRootCause(), clampConfidence(result.confidence()));

    repository.addIncidentTimeline(
        new AddTimelineParams(
            newId("tl"),
            incidentId,
            OffsetDateTime.now(),
            "RCA analysis completed",
            result.summary(),
            """
                {
                  "rcaAnalysisId": "%s",
                  "suspectedRootCause": "%s",
                  "confidence": "%s",
                  "matchedRules": %s,
                  "evidenceRefs": %s
                }
                """
                .formatted(
                    escapeJson(id),
                    escapeJson(result.suspectedRootCause()),
                    clampConfidence(result.confidence()).toPlainString(),
                    writeJson(result.matchedRules()),
                    writeJson(result.evidenceRefs()))));

    return repository
        .findAnalysis(tenantId, id)
        .map(record -> toResponse(record))
        .orElseThrow(() -> new AppException("RCA_NOT_FOUND", "RCA analysis not found after save"));
  }

  private void ensureIncidentExists(String tenantId, String incidentId) {
    if (repository.findIncident(tenantId, incidentId).isEmpty()) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }

  private RcaAnalysisResponse toResponse(RcaAnalysisRecord record) {
    List<RcaEvidence> evidence = readEvidence(record.evidenceJson());

    return new RcaAnalysisResponse(
        record.id(),
        record.incidentId(),
        record.status(),
        record.suspectedRootCause(),
        record.confidence(),
        record.summary(),
        evidence,
        matchedRules(evidence),
        evidenceRefs(evidence),
        record.modelVersion(),
        record.createdAt());
  }

  private static List<String> matchedRules(List<RcaEvidence> evidence) {
    if (evidence == null || evidence.isEmpty()) {
      return List.of();
    }

    return evidence.stream()
        .map(evidence -> evidence.ruleId())
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> evidenceRefs(List<RcaEvidence> evidence) {
    if (evidence == null || evidence.isEmpty()) {
      return List.of();
    }

    return evidence.stream()
        .flatMap(item -> extractEvidenceRefs(item).stream())
        .distinct()
        .toList();
  }

  private static List<String> extractEvidenceRefs(RcaEvidence evidence) {
    if (evidence == null || evidence.attributes() == null) {
      return List.of();
    }

    Object refs = evidence.attributes().get("evidenceRefs");
    if (refs instanceof Iterable<?> iterable) {
      List<String> out = new ArrayList<>();
      for (Object ref : iterable) {
        if (ref != null && !String.valueOf(ref).isBlank()) {
          out.add(String.valueOf(ref).trim());
        }
      }
      return List.copyOf(out);
    }

    return List.of();
  }

  private List<RcaEvidence> readEvidence(String evidenceJson) {
    try {
      if (evidenceJson == null || evidenceJson.isBlank()) {
        return List.of();
      }

      return objectMapper.readValue(evidenceJson, new TypeReference<List<RcaEvidence>>() {});
    } catch (Exception ex) {
      throw new AppException("RCA_EVIDENCE_INVALID", "RCA evidence is invalid");
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("RCA_EVIDENCE_INVALID", "Failed to serialize RCA evidence");
    }
  }

  private BigDecimal clampConfidence(BigDecimal confidence) {
    if (confidence == null) {
      return BigDecimal.ZERO;
    }

    if (confidence.compareTo(BigDecimal.ZERO) < 0) {
      return BigDecimal.ZERO;
    }

    if (confidence.compareTo(new BigDecimal("0.99")) > 0) {
      return new BigDecimal("0.99");
    }

    return confidence;
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
