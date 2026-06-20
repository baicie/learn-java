package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseIndexServiceTest {
  @Test
  void indexPublishedIncidentCase() {
    FakeIncidentCaseService caseService = new FakeIncidentCaseService("published");
    FakeKnowledgeBaseRepository repository = new FakeKnowledgeBaseRepository();

    KnowledgeBaseIndexService service =
        new KnowledgeBaseIndexService(
            caseService,
            new IncidentCaseChunkBuilder(new ObjectMapper()),
            repository,
            new HashingEmbeddingProvider(),
            new ObjectMapper());

    var response = service.indexIncidentCase("tenant_1", "icase_1");

    assertEquals("incident_case", response.sourceType());
    assertEquals("icase_1", response.sourceId());
    assertTrue(response.chunkCount() > 0);
    assertTrue(repository.chunks.size() > 0);
  }

  @Test
  void rejectNonPublishedIncidentCase() {
    FakeIncidentCaseService caseService = new FakeIncidentCaseService("draft");

    KnowledgeBaseIndexService service =
        new KnowledgeBaseIndexService(
            caseService,
            new IncidentCaseChunkBuilder(new ObjectMapper()),
            new FakeKnowledgeBaseRepository(),
            new HashingEmbeddingProvider(),
            new ObjectMapper());

    assertThrows(AppException.class, () -> service.indexIncidentCase("tenant_1", "icase_1"));
  }

  private static class FakeIncidentCaseService extends IncidentCaseService {
    private final String status;

    FakeIncidentCaseService(String status) {
      super(null, null, null);
      this.status = status;
    }

    @Override
    public IncidentCaseResponse get(String tenantId, String caseId) {
      return KnowledgeBaseTestFixtures.caseResponse(status);
    }
  }

  private static class FakeKnowledgeBaseRepository implements KnowledgeBaseRepository {
    KnowledgeBaseDocumentRecord document;
    final List<KnowledgeBaseChunkRecord> chunks = new ArrayList<>();

    @Override
    public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {
      document =
          new KnowledgeBaseDocumentRecord(
              command.id(),
              command.tenantId(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.status(),
              command.metadataJson(),
              OffsetDateTime.now(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) {
      return Optional.ofNullable(document);
    }

    @Override
    public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
        String tenantId, String sourceType, String sourceId) {
      return Optional.ofNullable(document)
          .filter(item -> item.sourceType().equals(sourceType) && item.sourceId().equals(sourceId));
    }

    @Override
    public void deleteChunksByDocument(String tenantId, String documentId) {
      chunks.clear();
    }

    @Override
    public void createChunk(KnowledgeBaseChunkCreateCommand command) {
      chunks.add(
          new KnowledgeBaseChunkRecord(
              command.id(),
              command.tenantId(),
              command.documentId(),
              command.chunkOrder(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.content(),
              command.contentHash(),
              command.tokenEstimate(),
              command.embeddingJson(),
              command.metadataJson(),
              command.status(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) {
      return chunks;
    }

    @Override
    public List<KnowledgeBaseChunkRecord> listCandidateChunks(
        String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
      return chunks;
    }

    @Override
    public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {}
  }
}
