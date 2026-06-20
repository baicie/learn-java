package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseSearchServiceTest {
  @Test
  void searchReturnsRelevantChunks() {
    ObjectMapper objectMapper = new ObjectMapper();
    HashingEmbeddingProvider embeddingProvider = new HashingEmbeddingProvider();
    KnowledgeBaseJson json = new KnowledgeBaseJson(objectMapper);

    FakeKnowledgeBaseRepository repository =
        new FakeKnowledgeBaseRepository(
            List.of(
                chunk(
                    "kbc_1",
                    "order service redis timeout",
                    json.write(embeddingProvider.embed("order service redis timeout"))),
                chunk(
                    "kbc_2",
                    "frontend css error",
                    json.write(embeddingProvider.embed("frontend css error")))));

    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            repository, embeddingProvider, new KnowledgeBaseScorer(), objectMapper);

    var response =
        service.search(
            "tenant_1",
            new KnowledgeBaseSearchRequest(
                "redis timeout", List.of("incident_case"), List.of(), 3, "alice"));

    assertEquals(1, response.results().size());
    assertEquals("kbc_1", response.results().get(0).chunkId());
    assertEquals(List.of("incident_case"), repository.capturedSourceTypes);
    assertTrue(repository.logged);
  }

  @Test
  void defaultSourceTypeIsIncidentCase() {
    FakeKnowledgeBaseRepository repository = new FakeKnowledgeBaseRepository(List.of());

    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            repository,
            new HashingEmbeddingProvider(),
            new KnowledgeBaseScorer(),
            new ObjectMapper());

    service.search(
        "tenant_1", new KnowledgeBaseSearchRequest("redis timeout", null, List.of(), 5, "alice"));

    assertEquals(List.of("incident_case"), repository.capturedSourceTypes);
  }

  @Test
  void blankSourceTypesFallbackToIncidentCase() {
    FakeKnowledgeBaseRepository repository = new FakeKnowledgeBaseRepository(List.of());

    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            repository,
            new HashingEmbeddingProvider(),
            new KnowledgeBaseScorer(),
            new ObjectMapper());

    service.search(
        "tenant_1",
        new KnowledgeBaseSearchRequest("redis timeout", List.of(" ", ""), List.of(), 5, "alice"));

    assertEquals(List.of("incident_case"), repository.capturedSourceTypes);
  }

  @Test
  void rejectInvalidSourceType() {
    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            new FakeKnowledgeBaseRepository(List.of()),
            new HashingEmbeddingProvider(),
            new KnowledgeBaseScorer(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.search(
                "tenant_1",
                new KnowledgeBaseSearchRequest(
                    "redis timeout", List.of("invalid"), List.of(), 5, "alice")));
  }

  @Test
  void rejectBlankQuery() {
    KnowledgeBaseSearchService service =
        new KnowledgeBaseSearchService(
            new FakeKnowledgeBaseRepository(List.of()),
            new HashingEmbeddingProvider(),
            new KnowledgeBaseScorer(),
            new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.search(
                "tenant_1",
                new KnowledgeBaseSearchRequest(
                    " ", List.of("incident_case"), List.of(), 5, "alice")));
  }

  private static KnowledgeBaseChunkRecord chunk(String id, String content, String embeddingJson) {
    return new KnowledgeBaseChunkRecord(
        id,
        "tenant_1",
        "kbd_1",
        1,
        "incident_case",
        "icase_1",
        "title",
        content,
        "hash",
        10,
        embeddingJson,
        "{}",
        "indexed",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeKnowledgeBaseRepository implements KnowledgeBaseRepository {
    private final List<KnowledgeBaseChunkRecord> chunks;
    boolean logged;
    List<String> capturedSourceTypes;
    List<String> capturedTags;
    int capturedCandidateLimit;

    FakeKnowledgeBaseRepository(List<KnowledgeBaseChunkRecord> chunks) {
      this.chunks = chunks;
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listCandidateChunks(
        String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
      this.capturedSourceTypes = sourceTypes;
      this.capturedTags = tags;
      this.capturedCandidateLimit = candidateLimit;
      return chunks;
    }

    @Override
    public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {
      logged = true;
    }

    @Override
    public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {}

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) {
      return Optional.empty();
    }

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
        String tenantId, String sourceType, String sourceId) {
      return Optional.empty();
    }

    @Override
    public void deleteChunksByDocument(String tenantId, String documentId) {}

    @Override
    public void createChunk(KnowledgeBaseChunkCreateCommand command) {}

    @Override
    public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) {
      return List.of();
    }
  }
}
