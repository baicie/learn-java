package io.aegisops.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertIngestService {
  private static final String OUTBOX_TARGET_APP = "worker";
  private static final String OUTBOX_JOB_NAME = "incident-aggregate";

  private final AlertFingerprintPolicy fingerprintPolicy;
  private final AlertIngestRepository repository;
  private final ObjectMapper objectMapper;
  private final OutboxWriter outboxWriter;

  public AlertIngestService(
      AlertFingerprintPolicy fingerprintPolicy,
      AlertIngestRepository repository,
      ObjectMapper objectMapper,
      OutboxWriter outboxWriter) {
    this.fingerprintPolicy = fingerprintPolicy;
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public AlertIngestResult ingest(String tenantId, AlertIngestRequest request) {
    validateTenant(tenantId);
    validate(request);

    String fingerprint = fingerprintPolicy.fingerprint(request);
    String aggregationKey = fingerprintPolicy.aggregationKey(request);

    AlertIngestResult result =
        repository.upsert(
            tenantId,
            request,
            fingerprint,
            aggregationKey,
            writeJson(request.labels() == null ? Map.of() : request.labels()),
            writeJson(request.rawPayload() == null ? Map.of() : request.rawPayload()));

    enqueueAggregation(tenantId, request, result);
    return result;
  }

  private void validateTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenant id is required");
    }
  }

  private void validate(AlertIngestRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("alert request is required");
    }
    if (request.source() == null || request.source().isBlank()) {
      throw new IllegalArgumentException("alert source is required");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new IllegalArgumentException("alert title is required");
    }
  }

  private void enqueueAggregation(
      String tenantId, AlertIngestRequest request, AlertIngestResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tenantId", tenantId);
    payload.put("alertId", result.alertId());
    payload.put("source", request.source());
    payload.put("sourceEventId", request.sourceEventId());
    payload.put("fingerprint", result.fingerprint());
    payload.put("aggregationKey", result.aggregationKey());
    payload.put("created", result.created());

    outboxWriter.enqueue(OUTBOX_TARGET_APP, OUTBOX_JOB_NAME, tenantId, payload);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid alert json", e);
    }
  }
}
