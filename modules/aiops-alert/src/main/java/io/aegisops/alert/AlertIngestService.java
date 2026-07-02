package io.aegisops.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Service for standardizing and persisting incoming alert events. */
@Service
public class AlertIngestService {
  private final AlertFingerprintPolicy fingerprintPolicy;
  private final AlertIngestRepository repository;
  private final ObjectMapper objectMapper;

  public AlertIngestService(
      AlertFingerprintPolicy fingerprintPolicy,
      AlertIngestRepository repository,
      ObjectMapper objectMapper) {
    this.fingerprintPolicy = fingerprintPolicy;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public AlertIngestResult ingest(String tenantId, AlertIngestRequest request) {
    validate(request);

    String fingerprint = fingerprintPolicy.fingerprint(request);
    String aggregationKey = fingerprintPolicy.aggregationKey(request);
    return repository.upsert(
        tenantId,
        request,
        fingerprint,
        aggregationKey,
        writeJson(request.labels() == null ? Map.of() : request.labels()),
        writeJson(request.rawPayload() == null ? Map.of() : request.rawPayload()));
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

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid alert json", e);
    }
  }
}
