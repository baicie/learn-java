package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentAlertContext;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentEvidenceContext;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import io.aegisops.ai.client.dto.AgentRcaContext;
import io.aegisops.ai.client.dto.AgentRunDetailResponse;
import io.aegisops.ai.client.dto.AgentTimelineContext;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.ai.client.dto.AiEvidenceRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.AiTimelineRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiDiagnosisService {
  private static final Logger log = LoggerFactory.getLogger(AiDiagnosisService.class);

  private final AiRepository repository;
  private final AiAgentClient agentClient;
  private final ObjectMapper objectMapper;
  private final AgentContractValidator contractValidator;
  private final AgentObservabilityExtractor observabilityExtractor;

  @Autowired
  public AiDiagnosisService(
      AiRepository repository,
      AiAgentClient agentClient,
      ObjectMapper objectMapper,
      @Autowired(required = false) AgentContractValidator contractValidator,
      @Autowired(required = false) AgentObservabilityExtractor observabilityExtractor) {
    this.repository = repository;
    this.agentClient = agentClient;
    this.objectMapper = objectMapper;
    this.contractValidator =
        contractValidator != null ? contractValidator : new AgentContractValidator();
    this.observabilityExtractor =
        observabilityExtractor != null
            ? observabilityExtractor
            : new AgentObservabilityExtractor(objectMapper);
  }

  public AiDiagnosisService(
      AiRepository repository, AiAgentClient agentClient, ObjectMapper objectMapper) {
    this(repository, agentClient, objectMapper, null, null);
  }

  public AiDiagnosisResponse latest(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);
    return repository
        .findLatestDiagnosis(tenantId, incidentId)
        .map(record -> toResponse(record))
        .orElseThrow(() -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found"));
  }

  public AgentRunDetailResponse latestRun(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);

    var run =
        repository
            .findLatestAgentRun(tenantId, incidentId)
            .orElseThrow(() -> new AppException("AGENT_RUN_NOT_FOUND", "Agent run not found"));

    return new AgentRunDetailResponse(
        run.id(),
        run.diagnosisId(),
        run.incidentId(),
        run.traceId(),
        run.contractVersion(),
        run.generationMode(),
        run.provider(),
        run.model(),
        run.status(),
        run.startedAt(),
        run.finishedAt(),
        run.durationMs(),
        run.fallbackReason(),
        run.safetyJson(),
        run.evalJson(),
        repository.listAgentRunSteps(run.id()),
        repository.listAgentEvalResults(run.id()),
        run.createdAt());
  }

  private void persistAgentObservability(
      String diagnosisId,
      String tenantId,
      String incidentId,
      String traceId,
      AgentDiagnosisResponse response) {
    try {
      var data =
          observabilityExtractor.extract(diagnosisId, tenantId, incidentId, traceId, response);

      if (data.run().isEmpty()) {
        return;
      }

      repository.saveAgentRun(data.run().get());

      if (!data.steps().isEmpty()) {
        repository.saveAgentRunSteps(data.steps());
      }

      if (!data.evalResults().isEmpty()) {
        repository.saveAgentEvalResults(data.evalResults());
      }
    } catch (Exception ex) {
      log.warn(
          "Failed to persist agent observability. tenantId={}, incidentId={}, diagnosisId={}, traceId={}",
          tenantId,
          incidentId,
          diagnosisId,
          traceId,
          ex);
    }
  }

  @Transactional
  public AiDiagnosisResponse diagnose(
      String tenantId, String incidentId, AiDiagnoseRequest request) {
    AiDiagnoseRequest normalized =
        request == null ? new AiDiagnoseRequest(false, "zh-CN") : request;

    AiIncidentRecord incident =
        repository
            .findIncident(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    if (!normalized.forceEnabled()) {
      var latest = repository.findLatestDiagnosis(tenantId, incidentId);
      if (latest.isPresent() && isReusableLatest(incident, latest.get())) {
        return toResponse(latest.get());
      }
    }

    List<AiAlertRecord> alerts = repository.listIncidentAlerts(tenantId, incidentId);
    AiRcaRecord rca = repository.findLatestRca(tenantId, incidentId).orElse(null);
    List<AiEvidenceRecord> evidence = repository.listDiagnosisEvidence(tenantId, incidentId);
    List<AiTimelineRecord> timeline = repository.listIncidentTimeline(tenantId, incidentId, 50);

    String diagnosisId = newId("diag");
    AgentDiagnosisRequest agentRequest =
        new AgentDiagnosisRequest(
            AgentContract.DIAGNOSIS_CONTRACT_VERSION,
            tenantId,
            incidentId,
            toAgentIncident(incident),
            alerts.stream().map(alert -> toAgentAlert(alert)).toList(),
            rca == null ? null : toAgentRca(rca),
            evidence.stream().map(record -> toAgentEvidence(record)).toList(),
            timeline.stream().map(record -> toAgentTimeline(record)).toList(),
            normalized.normalizedLocale(),
            UUID.randomUUID().toString(),
            diagnosisId);

    contractValidator.validateRequest(agentRequest);

    AgentDiagnosisResponse agentResponse =
        sanitizeAgentResponse(agentClient.diagnose(agentRequest));
    contractValidator.validateResponse(agentResponse);

    persistDiagnosisAndTimeline(tenantId, incidentId, diagnosisId, agentRequest, agentResponse);

    return repository
        .findDiagnosis(tenantId, diagnosisId)
        .map(record -> toResponse(record))
        .orElseThrow(
            () -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found after save"));
  }

  private void persistDiagnosisAndTimeline(
      String tenantId,
      String incidentId,
      String diagnosisId,
      AgentDiagnosisRequest agentRequest,
      AgentDiagnosisResponse agentResponse) {
    Map<String, Object> raw = agentResponse.raw() == null ? Map.of() : agentResponse.raw();
    List<String> evidenceRefs = extractList(raw.get("evidenceRefs"));
    List<String> matchedRules = extractList(raw.get("matchedRules"));
    repository.saveDiagnosis(
        new SaveDiagnosisCommand(
            diagnosisId,
            tenantId,
            incidentId,
            agentResponse,
            writeJson(agentRequest),
            writeJson(raw),
            writeJson(agentResponse.nextSteps()),
            writeJson(agentResponse.runbookSuggestions()),
            writeJson(agentResponse.risks())));
    persistAgentObservability(
        diagnosisId, tenantId, incidentId, agentRequest.traceId(), agentResponse);
    Map<String, Object> tlPayload = new HashMap<>();
    tlPayload.put("contractVersion", agentRequest.contractVersion());
    tlPayload.put("traceId", agentRequest.traceId());
    tlPayload.put("aiDiagnosisId", diagnosisId);
    tlPayload.put("provider", agentResponse.provider());
    tlPayload.put("model", agentResponse.model());
    tlPayload.put("agentName", agentResponse.agentName());
    tlPayload.put("rootCause", agentResponse.rootCause());
    tlPayload.put("evidenceRefs", evidenceRefs);
    tlPayload.put("matchedRules", matchedRules);
    repository.addIncidentTimeline(
        new TimelineCommand(
            newId("tl"),
            incidentId,
            OffsetDateTime.now(),
            "AI diagnosis completed",
            agentResponse.summary(),
            writeJson(tlPayload)));
  }

  private AgentDiagnosisResponse sanitizeAgentResponse(AgentDiagnosisResponse response) {
    if (response == null) {
      throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
    }
    Map<String, Object> raw = response.raw() == null ? Map.of() : response.raw();
    List<String> matchedRules = extractList(raw.get("matchedRules"));
    List<String> evidenceRefs = extractList(raw.get("evidenceRefs"));
    List<Map<String, Object>> timeline = extractMapList(raw.get("timeline"));
    return new AgentDiagnosisResponse(
        blankToDefault(response.contractVersion(), AgentContract.DIAGNOSIS_CONTRACT_VERSION),
        response.incidentId(),
        response.status() == null ? "completed" : response.status(),
        blankToDefault(response.provider(), "aiops-agent"),
        blankToDefault(response.model(), "langgraph-deterministic"),
        blankToDefault(response.agentName(), "aegisops_diagnosis_graph"),
        blankToDefault(response.summary(), "No summary generated."),
        blankToDefault(response.rootCause(), "No root cause generated."),
        blankToDefault(response.impact(), "Impact is unknown."),
        nvl(response.nextSteps()),
        nvl(response.runbookSuggestions()),
        nvl(response.risks()),
        matchedRules,
        evidenceRefs,
        timeline,
        raw,
        response.createdAt());
  }

  private static <T> List<T> nvl(List<T> v) {
    return v == null ? List.of() : v;
  }

  private List<String> extractList(Object value) {
    if (value instanceof Iterable<?> it) {
      List<String> out = new ArrayList<>();
      for (Object item : it) {
        if (item != null) out.add(String.valueOf(item));
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  private List<Map<String, Object>> extractMapList(Object value) {
    if (value instanceof Iterable<?> it) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : it) {
        if (item instanceof Map<?, ?> m) {
          out.add(objectMapper.convertValue(m, new TypeReference<Map<String, Object>>() {}));
        }
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  private boolean isReusableLatest(AiIncidentRecord incident, AiDiagnosisRecord latest) {
    if (latest.createdAt() == null) return false;
    OffsetDateTime baseline = incident.lastSeenAt();
    if (baseline == null) baseline = incident.updatedAt();
    if (baseline == null) baseline = incident.createdAt();
    return baseline != null && !latest.createdAt().isBefore(baseline);
  }

  private AgentIncidentContext toAgentIncident(AiIncidentRecord incident) {
    return new AgentIncidentContext(
        incident.id(),
        incident.title(),
        incident.summary(),
        incident.severity(),
        incident.status(),
        incident.source(),
        incident.primaryAssetId(),
        incident.aggregationKey(),
        incident.alertCount(),
        incident.suspectedRootCause(),
        incident.confidence(),
        incident.startedAt(),
        incident.detectedAt(),
        incident.lastSeenAt());
  }

  private AgentAlertContext toAgentAlert(AiAlertRecord alert) {
    return new AgentAlertContext(
        alert.id(),
        alert.source(),
        alert.sourceEventId(),
        alert.severity(),
        alert.title(),
        alert.description(),
        alert.assetId(),
        alert.entityType(),
        alert.entityName(),
        alert.fingerprint(),
        alert.labelsJson(),
        alert.startsAt());
  }

  private AgentRcaContext toAgentRca(AiRcaRecord rca) {
    List<String> ruleIds = List.of();
    List<String> refs = List.of();
    try {
      if (rca.evidenceJson() != null && !rca.evidenceJson().isBlank()) {
        List<Map<String, Object>> evList =
            objectMapper.readValue(
                rca.evidenceJson(), new TypeReference<List<Map<String, Object>>>() {});
        ruleIds =
            evList.stream()
                .map(item -> item.get("ruleId"))
                .filter(item -> item != null)
                .map(item -> String.valueOf(item))
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        refs = new ArrayList<>();
        for (Map<String, Object> item : evList) {
          Object attrs = item.get("attributes");
          if (attrs instanceof Map<?, ?> m) {
            Object r = m.get("evidenceRefs");
            if (r instanceof Iterable<?> it) {
              for (Object x : it) {
                if (x != null && !String.valueOf(x).isBlank()) {
                  refs.add(String.valueOf(x).trim());
                }
              }
            }
          }
        }
        refs = refs.stream().distinct().toList();
      }
    } catch (Exception ignored) {
    }
    return new AgentRcaContext(
        rca.id(),
        rca.suspectedRootCause(),
        rca.confidence(),
        rca.summary(),
        rca.evidenceJson(),
        ruleIds,
        refs,
        rca.modelVersion(),
        rca.createdAt());
  }

  private AgentEvidenceContext toAgentEvidence(AiEvidenceRecord evidence) {
    return new AgentEvidenceContext(
        evidence.id(),
        evidence.evidenceKey(),
        evidence.source(),
        evidence.evidenceType(),
        evidence.title(),
        evidence.summary(),
        evidence.timeRangeStart(),
        evidence.timeRangeEnd(),
        evidence.confidence(),
        evidence.payloadJson());
  }

  private AgentTimelineContext toAgentTimeline(AiTimelineRecord tl) {
    return new AgentTimelineContext(
        tl.id(),
        tl.eventTime(),
        tl.eventType(),
        tl.title(),
        tl.description(),
        tl.source(),
        tl.payloadJson());
  }

  private AiDiagnosisResponse toResponse(AiDiagnosisRecord record) {
    Map<String, Object> raw = readMap(record.responseRawJson());

    return new AiDiagnosisResponse(
        record.id(),
        record.incidentId(),
        record.status(),
        record.provider(),
        record.model(),
        record.agentName(),
        record.summary(),
        record.rootCause(),
        record.impact(),
        readStringList(record.nextStepsJson()),
        readStringList(record.runbookSuggestionsJson()),
        readStringList(record.risksJson()),
        readStringListFromRaw(raw, "matchedRules"),
        readStringListFromRaw(raw, "evidenceRefs"),
        readTimelineFromRaw(raw),
        raw,
        record.createdAt());
  }

  private Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private List<String> readStringListFromRaw(Map<String, Object> raw, String key) {
    Object value = raw == null ? null : raw.get(key);
    if (value instanceof Iterable<?> iterable) {
      List<String> out = new ArrayList<>();
      for (Object item : iterable) {
        if (item != null && !String.valueOf(item).isBlank()) {
          out.add(String.valueOf(item).trim());
        }
      }
      return List.copyOf(out);
    }
    return List.of();
  }

  private List<Map<String, Object>> readTimelineFromRaw(Map<String, Object> raw) {
    Object value = raw == null ? null : raw.get("timeline");
    if (!(value instanceof Iterable<?> iterable)) {
      return List.of();
    }

    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : iterable) {
      if (item instanceof Map<?, ?> map) {
        out.add(objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {}));
      }
    }
    return List.copyOf(out);
  }

  private void ensureIncidentExists(String tenantId, String incidentId) {
    if (repository.findIncident(tenantId, incidentId).isEmpty()) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("AI_DIAGNOSIS_INVALID", "Failed to serialize AI diagnosis payload");
    }
  }

  private List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new AppException("AI_DIAGNOSIS_INVALID", "AI diagnosis JSON is invalid");
    }
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
