package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import io.aegisops.execution.dto.KnowledgeBaseSearchResult;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseSearchService {
  private final KnowledgeBaseRepository repository;
  private final EmbeddingProvider embeddingProvider;
  private final KnowledgeBaseScorer scorer;
  private final KnowledgeBaseJson json;

  public KnowledgeBaseSearchService(
      KnowledgeBaseRepository repository,
      EmbeddingProvider embeddingProvider,
      KnowledgeBaseScorer scorer,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.embeddingProvider = embeddingProvider;
    this.scorer = scorer;
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  public KnowledgeBaseSearchResponse search(String tenantId, KnowledgeBaseSearchRequest request) {
    validateRequest(request);

    int topK = normalizeTopK(request.topK());
    List<Double> queryEmbedding = embeddingProvider.embed(request.query());

    List<KnowledgeBaseSearchResult> results =
        repository
            .listCandidateChunks(
                tenantId,
                normalizeSourceTypes(request.sourceTypes()),
                normalizeTags(request.tags()),
                Math.max(topK * 20, 100))
            .stream()
            .map(
                chunk -> {
                  double vectorScore =
                      scorer.cosine(queryEmbedding, json.readDoubleList(chunk.embeddingJson()));
                  double keywordScore = scorer.keywordScore(request.query(), chunk.content());
                  double score = scorer.hybridScore(vectorScore, keywordScore);

                  return new KnowledgeBaseSearchResult(
                      chunk.documentId(),
                      chunk.id(),
                      chunk.sourceType(),
                      chunk.sourceId(),
                      chunk.title(),
                      chunk.content(),
                      round(score),
                      round(vectorScore),
                      round(keywordScore),
                      chunk.metadataJson());
                })
            .filter(result -> result.score() > 0.0d)
            .sorted(Comparator.comparingDouble(KnowledgeBaseSearchResult::score).reversed())
            .limit(topK)
            .toList();

    repository.createSearchLog(
        new KnowledgeBaseSearchLogCreateCommand(
            newId("kbs"),
            tenantId,
            request.query(),
            json.write(normalizeSourceTypes(request.sourceTypes())),
            json.write(normalizeTags(request.tags())),
            topK,
            results.size(),
            blankToDefault(request.createdBy(), "system")));

    return new KnowledgeBaseSearchResponse(request.query(), topK, results);
  }

  private void validateRequest(KnowledgeBaseSearchRequest request) {
    if (request == null || request.query() == null || request.query().isBlank()) {
      throw new AppException("KB_SEARCH_QUERY_REQUIRED", "Knowledge base search query is required");
    }
  }

  private int normalizeTopK(Integer value) {
    if (value == null) {
      return 5;
    }
    return Math.max(1, Math.min(value, 50));
  }

  private List<String> normalizeSourceTypes(List<String> sourceTypes) {
    if (sourceTypes == null || sourceTypes.isEmpty()) {
      return List.of("incident_case");
    }

    return sourceTypes.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase())
        .filter(value -> List.of("incident_case", "postmortem", "manual").contains(value))
        .distinct()
        .toList();
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }

    return tags.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(this::normalizeTag)
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

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
