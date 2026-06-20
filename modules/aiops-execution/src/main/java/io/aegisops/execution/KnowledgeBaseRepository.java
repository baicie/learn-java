package io.aegisops.execution;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository {
  void upsertDocument(KnowledgeBaseDocumentCreateCommand command);

  Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId);

  Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
      String tenantId, String sourceType, String sourceId);

  void deleteChunksByDocument(String tenantId, String documentId);

  void createChunk(KnowledgeBaseChunkCreateCommand command);

  List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId);

  List<KnowledgeBaseChunkRecord> listCandidateChunks(
      String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit);

  void createSearchLog(KnowledgeBaseSearchLogCreateCommand command);
}
