package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import io.aegisops.execution.dto.AgentMemoryResponse;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import io.aegisops.execution.dto.AgentMemorySearchResponse;
import io.aegisops.execution.dto.AgentMemorySearchResult;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentMemoryService {
  private final AgentMemoryRepository repository;
  private final AgentMemoryPolicy policy;
  private final AgentMemoryJson json;
  private final AgentMemoryScorer scorer;

  public AgentMemoryService(
      AgentMemoryRepository repository,
      AgentMemoryPolicy policy,
      AgentMemoryJson json,
      AgentMemoryScorer scorer) {
    this.repository = repository;
    this.policy = policy;
    this.json = json;
    this.scorer = scorer;
  }

  @Transactional
  public AgentMemoryResponse createInternal(AgentMemoryCreateRequest request) {
    policy.validateCreate(request);

    String tenantId = request.tenantId().trim();
    String memoryId = newId("agm");

    repository.create(
        new AgentMemoryCreateCommand(
            memoryId,
            tenantId,
            policy.normalize(request.scopeType(), "tenant"),
            blankToNull(request.scopeId()),
            policy.normalize(request.memoryType(), "incident_summary"),
            policy.normalize(request.sourceType(), "diagnosis"),
            blankToNull(request.sourceId()),
            request.title().trim(),
            request.content().trim(),
            json.write(normalizeTags(request.tags())),
            normalizeConfidence(request.confidence()),
            "active",
            blankToDefault(request.createdBy(), "agent"),
            calculateExpiresAt(request.ttlSeconds())));

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            memoryId,
            "created",
            "Agent memory created",
            blankToDefault(request.createdBy(), "agent"),
            "{}"));

    return toResponse(load(tenantId, memoryId));
  }

  public AgentMemoryResponse get(String tenantId, String memoryId) {
    return toResponse(load(tenantId, memoryId));
  }

  @Transactional
  public AgentMemoryResponse archive(String tenantId, String memoryId) {
    AgentMemoryRecord memory = load(tenantId, memoryId);

    boolean updated = repository.archive(tenantId, memory.id());
    if (!updated) {
      throw new AppException("AGENT_MEMORY_ARCHIVE_FAILED", "Memory was not archived");
    }

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            memoryId,
            "archived",
            "Agent memory archived",
            "system",
            "{}"));

    return get(tenantId, memoryId);
  }

  @Transactional
  public AgentMemorySearchResponse searchInternal(AgentMemorySearchRequest request) {
    validateSearchRequest(request);

    String tenantId = request.tenantId().trim();
    int topK = normalizeTopK(request.topK());

    List<String> tags = normalizeTags(request.tags());
    List<String> memoryTypes = normalizeMemoryTypes(request.memoryTypes());

    List<AgentMemorySearchResult> results =
        repository
            .listActiveCandidates(
                new ListActiveMemoryParams(
                    tenantId,
                    policy.normalize(request.scopeType(), ""),
                    blankToNull(request.scopeId()),
                    memoryTypes,
                    tags,
                    Math.max(topK * 20, 100)))
            .stream()
            .map(memory -> toSearchResult(request.query(), memory))
            .filter(result -> result.score() > 0)
            .sorted(Comparator.comparingDouble(AgentMemorySearchResult::score).reversed())
            .limit(topK)
            .toList();

    repository.createEvent(
        new AgentMemoryEventCreateCommand(
            newId("agme"),
            tenantId,
            null,
            "searched",
            "Agent memory searched",
            blankToDefault(request.createdBy(), "agent"),
            json.write(
                java.util.Map.of(
                    "query", request.query(),
                    "resultCount", results.size(),
                    "topK", topK))));

    return new AgentMemorySearchResponse(request.query(), topK, results);
  }

  private AgentMemorySearchResult toSearchResult(String query, AgentMemoryRecord memory) {
    double score = scorer.score(query, memory.title(), memory.content(), memory.confidence());

    return new AgentMemorySearchResult(
        memory.id(),
        memory.scopeType(),
        memory.scopeId(),
        memory.memoryType(),
        memory.sourceType(),
        memory.sourceId(),
        memory.title(),
        memory.content(),
        json.readStringList(memory.tagsJson()),
        memory.confidence(),
        score,
        memory.createdBy());
  }

  private AgentMemoryRecord load(String tenantId, String memoryId) {
    return repository
        .find(tenantId, memoryId)
        .orElseThrow(() -> new AppException("AGENT_MEMORY_NOT_FOUND", "Agent memory not found"));
  }

  private void validateSearchRequest(AgentMemorySearchRequest request) {
    if (request == null) {
      throw new AppException(
          "AGENT_MEMORY_SEARCH_REQUEST_REQUIRED", "Memory search request is required");
    }
    if (request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("AGENT_MEMORY_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.query() == null || request.query().isBlank()) {
      throw new AppException("AGENT_MEMORY_QUERY_REQUIRED", "Memory query is required");
    }
  }

  private List<String> normalizeMemoryTypes(List<String> memoryTypes) {
    if (memoryTypes == null || memoryTypes.isEmpty()) {
      return List.of();
    }

    return memoryTypes.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> policy.normalize(value, ""))
        .distinct()
        .toList();
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }

    return tags.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(tag -> normalizeTag(tag))
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private String normalizeTag(String value) {
    return value
        .trim()
        .toLowerCase()
        .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
        .replaceAll("^-+", "")
        .replaceAll("-+$", "");
  }

  private double normalizeConfidence(Double value) {
    if (value == null) {
      return 0.0d;
    }
    return Math.max(0.0d, Math.min(value, 1.0d));
  }

  private int normalizeTopK(Integer topK) {
    if (topK == null) {
      return 5;
    }
    return Math.max(1, Math.min(topK, 50));
  }

  private OffsetDateTime calculateExpiresAt(Integer ttlSeconds) {
    if (ttlSeconds == null || ttlSeconds <= 0) {
      return null;
    }
    return OffsetDateTime.now().plusSeconds(Math.min(ttlSeconds, 365 * 24 * 3600));
  }

  private AgentMemoryResponse toResponse(AgentMemoryRecord record) {
    return new AgentMemoryResponse(
        record.id(),
        record.scopeType(),
        record.scopeId(),
        record.memoryType(),
        record.sourceType(),
        record.sourceId(),
        record.title(),
        record.content(),
        json.readStringList(record.tagsJson()),
        record.confidence(),
        record.status(),
        record.createdBy(),
        record.expiresAt(),
        record.createdAt(),
        record.updatedAt());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
