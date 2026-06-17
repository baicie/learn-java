package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentAlertContext;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import io.aegisops.ai.client.dto.AgentRcaContext;
import io.aegisops.ai.client.dto.AgentRunDetailResponse;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public AiDiagnosisService(
            AiRepository repository,
            AiAgentClient agentClient,
            ObjectMapper objectMapper) {
        this(repository, agentClient, objectMapper, new AgentContractValidator(),
                new AgentObservabilityExtractor(objectMapper));
    }

    public AiDiagnosisService(
            AiRepository repository,
            AiAgentClient agentClient,
            ObjectMapper objectMapper,
            AgentContractValidator contractValidator) {
        this(repository, agentClient, objectMapper, contractValidator,
                new AgentObservabilityExtractor(objectMapper));
    }

    public AiDiagnosisService(
            AiRepository repository,
            AiAgentClient agentClient,
            ObjectMapper objectMapper,
            AgentContractValidator contractValidator,
            AgentObservabilityExtractor observabilityExtractor) {
        this.repository = repository;
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
        this.contractValidator = contractValidator;
        this.observabilityExtractor = observabilityExtractor;
    }

    public AiDiagnosisResponse latest(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);
        return repository
                .findLatestDiagnosis(tenantId, incidentId)
                .map(this::toResponse)
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
            var data = observabilityExtractor.extract(diagnosisId, tenantId, incidentId, traceId, response);

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

        AgentDiagnosisRequest agentRequest =
                new AgentDiagnosisRequest(
                        AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                        tenantId,
                        incidentId,
                        toAgentIncident(incident),
                        alerts.stream().map(this::toAgentAlert).toList(),
                        rca == null ? null : toAgentRca(rca),
                        normalized.normalizedLocale(),
                        UUID.randomUUID().toString());

        contractValidator.validateRequest(agentRequest);

        AgentDiagnosisResponse agentResponse =
                sanitizeAgentResponse(agentClient.diagnose(agentRequest));
        contractValidator.validateResponse(agentResponse);

        String diagnosisId = newId("diag");
        String requestJson = writeJson(agentRequest);
        String rawJson = writeJson(agentResponse.raw() == null ? Map.of() : agentResponse.raw());
        String nextStepsJson = writeJson(agentResponse.nextSteps());
        String runbookSuggestionsJson = writeJson(agentResponse.runbookSuggestions());
        String risksJson = writeJson(agentResponse.risks());

        repository.saveDiagnosis(
                new SaveDiagnosisCommand(
                        diagnosisId,
                        tenantId,
                        incidentId,
                        agentResponse,
                        requestJson,
                        rawJson,
                        nextStepsJson,
                        runbookSuggestionsJson,
                        risksJson));

        persistAgentObservability(
                diagnosisId,
                tenantId,
                incidentId,
                agentRequest.traceId(),
                agentResponse);

        repository.addIncidentTimeline(
                new TimelineCommand(
                        newId("tl"),
                        incidentId,
                        OffsetDateTime.now(),
                        "AI diagnosis completed",
                        agentResponse.summary(),
                        writeJson(
                                Map.of(
                                        "contractVersion", agentResponse.contractVersion(),
                                        "traceId", agentRequest.traceId(),
                                        "aiDiagnosisId", diagnosisId,
                                        "provider", agentResponse.provider(),
                                        "model", agentResponse.model(),
                                        "agentName", agentResponse.agentName(),
                                        "rootCause", agentResponse.rootCause()))));

        return repository
                .findDiagnosis(tenantId, diagnosisId)
                .map(this::toResponse)
                .orElseThrow(
                        () -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found after save"));
    }

    private AgentDiagnosisResponse sanitizeAgentResponse(AgentDiagnosisResponse response) {
        if (response == null) {
            throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
        }

        return new AgentDiagnosisResponse(
                blankToDefault(response.contractVersion(), AgentContract.DIAGNOSIS_CONTRACT_VERSION),
                blankToDefault(response.provider(), "aiops-agent"),
                blankToDefault(response.model(), "langgraph-deterministic"),
                blankToDefault(response.agentName(), "aegisops_diagnosis_graph"),
                blankToDefault(response.summary(), "No summary generated."),
                blankToDefault(response.rootCause(), "No root cause generated."),
                blankToDefault(response.impact(), "Impact is unknown."),
                response.nextSteps() == null ? List.of() : response.nextSteps(),
                response.runbookSuggestions() == null ? List.of() : response.runbookSuggestions(),
                response.risks() == null ? List.of() : response.risks(),
                response.raw() == null ? Map.of() : response.raw());
    }

    private boolean isReusableLatest(AiIncidentRecord incident, AiDiagnosisRecord latest) {
        if (latest.createdAt() == null) {
            return false;
        }

        OffsetDateTime baseline = incident.lastSeenAt();
        if (baseline == null) {
            baseline = incident.updatedAt();
        }
        if (baseline == null) {
            baseline = incident.createdAt();
        }

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
        return new AgentRcaContext(
                rca.id(),
                rca.suspectedRootCause(),
                rca.confidence(),
                rca.summary(),
                rca.evidenceJson(),
                rca.modelVersion(),
                rca.createdAt());
    }

    private AiDiagnosisResponse toResponse(AiDiagnosisRecord record) {
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
                record.createdAt());
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
