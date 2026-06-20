package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseIndexResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseIndexService {
  private final IncidentCaseService incidentCaseService;
  private final IncidentCaseChunkBuilder chunkBuilder;
  private final KnowledgeBaseRepository repository;
  private final EmbeddingProvider embeddingProvider;
  private final KnowledgeBaseJson json;

  public KnowledgeBaseIndexService(
      IncidentCaseService incidentCaseService,
      IncidentCaseChunkBuilder chunkBuilder,
      KnowledgeBaseRepository repository,
      EmbeddingProvider embeddingProvider,
      ObjectMapper objectMapper) {
    this.incidentCaseService = incidentCaseService;
    this.chunkBuilder = chunkBuilder;
    this.repository = repository;
    this.embeddingProvider = embeddingProvider;
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  @Transactional
  public KnowledgeBaseIndexResponse indexIncidentCase(String tenantId, String caseId) {
    IncidentCaseResponse incidentCase = incidentCaseService.get(tenantId, caseId);

    if (!"published".equals(incidentCase.status())) {
      throw new AppException(
          "KB_INDEX_CASE_STATUS_INVALID", "Only published incident case can be indexed");
    }

    String documentId =
        repository
            .findDocumentBySource(tenantId, "incident_case", caseId)
            .map(item -> item.id())
            .orElseGet(() -> newId("kbd"));

    repository.upsertDocument(
        new KnowledgeBaseDocumentCreateCommand(
            documentId,
            tenantId,
            "incident_case",
            caseId,
            incidentCase.title(),
            "indexed",
            json.write(
                Map.of(
                    "incidentId",
                    incidentCase.incidentId(),
                    "severity",
                    value(incidentCase.severity()),
                    "qualityScore",
                    incidentCase.qualityScore(),
                    "tags",
                    incidentCase.tags()))));

    repository.deleteChunksByDocument(tenantId, documentId);

    var chunks = chunkBuilder.build(incidentCase);
    for (var chunk : chunks) {
      String embeddingJson = json.write(embeddingProvider.embed(chunk.content()));

      repository.createChunk(
          new KnowledgeBaseChunkCreateCommand(
              newId("kbc"),
              tenantId,
              documentId,
              chunk.chunkOrder(),
              "incident_case",
              caseId,
              chunk.title(),
              chunk.content(),
              sha256(chunk.content()),
              estimateTokens(chunk.content()),
              embeddingJson,
              chunk.metadataJson(),
              "indexed"));
    }

    return new KnowledgeBaseIndexResponse(
        documentId, "incident_case", caseId, chunks.size(), "indexed");
  }

  public KnowledgeBaseIndexResponse getDocumentBySource(
      String tenantId, String sourceType, String sourceId) {
    var document =
        repository
            .findDocumentBySource(tenantId, sourceType, sourceId)
            .orElseThrow(
                () ->
                    new AppException("KB_DOCUMENT_NOT_FOUND", "Knowledge base document not found"));

    int chunkCount = repository.listChunksByDocument(tenantId, document.id()).size();

    return new KnowledgeBaseIndexResponse(
        document.id(), document.sourceType(), document.sourceId(), chunkCount, document.status());
  }

  public KnowledgeBaseIndexResponse getDocument(String tenantId, String documentId) {
    var document =
        repository
            .findDocument(tenantId, documentId)
            .orElseThrow(
                () ->
                    new AppException("KB_DOCUMENT_NOT_FOUND", "Knowledge base document not found"));

    int chunkCount = repository.listChunksByDocument(tenantId, document.id()).size();

    return new KnowledgeBaseIndexResponse(
        document.id(), document.sourceType(), document.sourceId(), chunkCount, document.status());
  }

  private int estimateTokens(String content) {
    if (content == null || content.isBlank()) {
      return 0;
    }
    return Math.max(1, content.length() / 4);
  }

  private String sha256(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new AppException("KB_CONTENT_HASH_FAILED", "Failed to hash content");
    }
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
