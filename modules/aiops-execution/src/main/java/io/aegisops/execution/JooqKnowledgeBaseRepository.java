package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.public_.Tables.KB_CHUNK;
import static io.aegisops.persistence.jooq.public_.Tables.KB_DOCUMENT;
import static io.aegisops.persistence.jooq.public_.Tables.KB_SEARCH_LOG;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseChunkRecord;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentRecord;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqKnowledgeBaseRepository implements KnowledgeBaseRepository {
  private final DSLContext dsl;

  public JooqKnowledgeBaseRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void upsertDocument(KnowledgeBaseDocumentCreateCommand command) {
    dsl.insertInto(KB_DOCUMENT)
        .set(KB_DOCUMENT.ID, command.id())
        .set(KB_DOCUMENT.TENANT_ID, command.tenantId())
        .set(KB_DOCUMENT.SOURCE_TYPE, command.sourceType())
        .set(KB_DOCUMENT.SOURCE_ID, command.sourceId())
        .set(KB_DOCUMENT.TITLE, command.title())
        .set(KB_DOCUMENT.STATUS, command.status())
        .set(KB_DOCUMENT.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_DOCUMENT.INDEXED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(KB_DOCUMENT.TENANT_ID, KB_DOCUMENT.SOURCE_TYPE, KB_DOCUMENT.SOURCE_ID)
        .doUpdate()
        .set(KB_DOCUMENT.TITLE, command.title())
        .set(KB_DOCUMENT.STATUS, command.status())
        .set(KB_DOCUMENT.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_DOCUMENT.INDEXED_AT, DSL.currentOffsetDateTime())
        .set(KB_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<KnowledgeBaseDocumentRecord> findDocument(String tenantId, String documentId) {
    return selectDocument()
        .where(KB_DOCUMENT.TENANT_ID.eq(tenantId))
        .and(KB_DOCUMENT.ID.eq(documentId))
        .fetchOptional(this::toDocumentRecord);
  }

  @Override
  public Optional<KnowledgeBaseDocumentRecord> findDocumentBySource(
      String tenantId, String sourceType, String sourceId) {
    return selectDocument()
        .where(KB_DOCUMENT.TENANT_ID.eq(tenantId))
        .and(KB_DOCUMENT.SOURCE_TYPE.eq(sourceType))
        .and(KB_DOCUMENT.SOURCE_ID.eq(sourceId))
        .fetchOptional(this::toDocumentRecord);
  }

  @Override
  public void deleteChunksByDocument(String tenantId, String documentId) {
    dsl.deleteFrom(KB_CHUNK)
        .where(KB_CHUNK.TENANT_ID.eq(tenantId))
        .and(KB_CHUNK.DOCUMENT_ID.eq(documentId))
        .execute();
  }

  @Override
  public void createChunk(KnowledgeBaseChunkCreateCommand command) {
    dsl.insertInto(KB_CHUNK)
        .set(KB_CHUNK.ID, command.id())
        .set(KB_CHUNK.TENANT_ID, command.tenantId())
        .set(KB_CHUNK.DOCUMENT_ID, command.documentId())
        .set(KB_CHUNK.CHUNK_ORDER, command.chunkOrder())
        .set(KB_CHUNK.SOURCE_TYPE, command.sourceType())
        .set(KB_CHUNK.SOURCE_ID, command.sourceId())
        .set(KB_CHUNK.TITLE, command.title())
        .set(KB_CHUNK.CONTENT, command.content())
        .set(KB_CHUNK.CONTENT_HASH, command.contentHash())
        .set(KB_CHUNK.TOKEN_ESTIMATE, command.tokenEstimate())
        .set(KB_CHUNK.EMBEDDING, jsonbValue(command.embeddingJson()))
        .set(KB_CHUNK.METADATA, jsonbValue(command.metadataJson()))
        .set(KB_CHUNK.STATUS, command.status())
        .set(KB_CHUNK.CREATED_AT, DSL.currentOffsetDateTime())
        .set(KB_CHUNK.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<KnowledgeBaseChunkRecord> listChunksByDocument(String tenantId, String documentId) {
    return selectChunk()
        .where(KB_CHUNK.TENANT_ID.eq(tenantId))
        .and(KB_CHUNK.DOCUMENT_ID.eq(documentId))
        .orderBy(KB_CHUNK.CHUNK_ORDER.asc())
        .fetch(this::toChunkRecord);
  }

  @Override
  public List<KnowledgeBaseChunkRecord> listCandidateChunks(
      String tenantId, List<String> sourceTypes, List<String> tags, int candidateLimit) {
    Condition condition = KB_CHUNK.TENANT_ID.eq(tenantId).and(KB_CHUNK.STATUS.eq("indexed"));

    if (sourceTypes != null && !sourceTypes.isEmpty()) {
      condition = condition.and(KB_CHUNK.SOURCE_TYPE.in(sourceTypes));
    }

    if (tags != null && !tags.isEmpty()) {
      for (String tag : tags) {
        condition = condition.and(DSL.lower(KB_CHUNK.CONTENT).contains(tag.toLowerCase()));
      }
    }

    return selectChunk()
        .where(condition)
        .orderBy(KB_CHUNK.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(candidateLimit, 500)))
        .fetch(this::toChunkRecord);
  }

  @Override
  public void createSearchLog(KnowledgeBaseSearchLogCreateCommand command) {
    dsl.insertInto(KB_SEARCH_LOG)
        .set(KB_SEARCH_LOG.ID, command.id())
        .set(KB_SEARCH_LOG.TENANT_ID, command.tenantId())
        .set(KB_SEARCH_LOG.QUERY, command.query())
        .set(KB_SEARCH_LOG.SOURCE_TYPES, jsonbValue(command.sourceTypesJson()))
        .set(KB_SEARCH_LOG.TAGS, jsonbValue(command.tagsJson()))
        .set(KB_SEARCH_LOG.TOP_K, command.topK())
        .set(KB_SEARCH_LOG.RESULT_COUNT, command.resultCount())
        .set(KB_SEARCH_LOG.CREATED_BY, command.createdBy())
        .set(KB_SEARCH_LOG.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private org.jooq.SelectJoinStep<?> selectDocument() {
    return dsl.select(
            KB_DOCUMENT.ID,
            KB_DOCUMENT.TENANT_ID,
            KB_DOCUMENT.SOURCE_TYPE,
            KB_DOCUMENT.SOURCE_ID,
            KB_DOCUMENT.TITLE,
            KB_DOCUMENT.STATUS,
            KB_DOCUMENT.METADATA.cast(String.class).as("metadata_json"),
            KB_DOCUMENT.INDEXED_AT,
            KB_DOCUMENT.CREATED_AT,
            KB_DOCUMENT.UPDATED_AT)
        .from(KB_DOCUMENT);
  }

  private org.jooq.SelectJoinStep<?> selectChunk() {
    return dsl.select(
            KB_CHUNK.ID,
            KB_CHUNK.TENANT_ID,
            KB_CHUNK.DOCUMENT_ID,
            KB_CHUNK.CHUNK_ORDER,
            KB_CHUNK.SOURCE_TYPE,
            KB_CHUNK.SOURCE_ID,
            KB_CHUNK.TITLE,
            KB_CHUNK.CONTENT,
            KB_CHUNK.CONTENT_HASH,
            KB_CHUNK.TOKEN_ESTIMATE,
            KB_CHUNK.EMBEDDING.cast(String.class).as("embedding_json"),
            KB_CHUNK.METADATA.cast(String.class).as("metadata_json"),
            KB_CHUNK.STATUS,
            KB_CHUNK.CREATED_AT,
            KB_CHUNK.UPDATED_AT)
        .from(KB_CHUNK);
  }

  private KnowledgeBaseDocumentRecord toDocumentRecord(org.jooq.Record record) {
    return new KnowledgeBaseDocumentRecord(
        record.get(KB_DOCUMENT.ID),
        record.get(KB_DOCUMENT.TENANT_ID),
        record.get(KB_DOCUMENT.SOURCE_TYPE),
        record.get(KB_DOCUMENT.SOURCE_ID),
        record.get(KB_DOCUMENT.TITLE),
        record.get(KB_DOCUMENT.STATUS),
        record.get("metadata_json", String.class),
        record.get(KB_DOCUMENT.INDEXED_AT),
        record.get(KB_DOCUMENT.CREATED_AT),
        record.get(KB_DOCUMENT.UPDATED_AT));
  }

  private KnowledgeBaseChunkRecord toChunkRecord(org.jooq.Record record) {
    return new KnowledgeBaseChunkRecord(
        record.get(KB_CHUNK.ID),
        record.get(KB_CHUNK.TENANT_ID),
        record.get(KB_CHUNK.DOCUMENT_ID),
        value(record.get(KB_CHUNK.CHUNK_ORDER)),
        record.get(KB_CHUNK.SOURCE_TYPE),
        record.get(KB_CHUNK.SOURCE_ID),
        record.get(KB_CHUNK.TITLE),
        record.get(KB_CHUNK.CONTENT),
        record.get(KB_CHUNK.CONTENT_HASH),
        value(record.get(KB_CHUNK.TOKEN_ESTIMATE)),
        record.get("embedding_json", String.class),
        record.get("metadata_json", String.class),
        record.get(KB_CHUNK.STATUS),
        record.get(KB_CHUNK.CREATED_AT),
        record.get(KB_CHUNK.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
