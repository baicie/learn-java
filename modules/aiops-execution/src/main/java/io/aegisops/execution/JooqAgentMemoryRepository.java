package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AGENT_MEMORY;
import static io.aegisops.persistence.jooq.Tables.AGENT_MEMORY_EVENT;

import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentMemoryRepository implements AgentMemoryRepository {
  private final DSLContext dsl;

  public JooqAgentMemoryRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void create(AgentMemoryCreateCommand command) {
    dsl.insertInto(AGENT_MEMORY)
        .set(AGENT_MEMORY.ID, command.id())
        .set(AGENT_MEMORY.TENANT_ID, command.tenantId())
        .set(AGENT_MEMORY.SCOPE_TYPE, command.scopeType())
        .set(AGENT_MEMORY.SCOPE_ID, command.scopeId())
        .set(AGENT_MEMORY.MEMORY_TYPE, command.memoryType())
        .set(AGENT_MEMORY.SOURCE_TYPE, command.sourceType())
        .set(AGENT_MEMORY.SOURCE_ID, command.sourceId())
        .set(AGENT_MEMORY.TITLE, command.title())
        .set(AGENT_MEMORY.CONTENT, command.content())
        .set(AGENT_MEMORY.TAGS, jsonbValue(command.tagsJson()))
        .set(AGENT_MEMORY.CONFIDENCE, BigDecimal.valueOf(command.confidence()))
        .set(AGENT_MEMORY.STATUS, command.status())
        .set(AGENT_MEMORY.CREATED_BY, command.createdBy())
        .set(AGENT_MEMORY.EXPIRES_AT, command.expiresAt())
        .set(AGENT_MEMORY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AGENT_MEMORY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<AgentMemoryRecord> find(String tenantId, String memoryId) {
    return selectMemory()
        .where(AGENT_MEMORY.TENANT_ID.eq(tenantId))
        .and(AGENT_MEMORY.ID.eq(memoryId))
        .fetchOptional(this::toRecord);
  }

  @Override
  public List<AgentMemoryRecord> listActiveCandidates(
      String tenantId,
      String scopeType,
      String scopeId,
      List<String> memoryTypes,
      List<String> tags,
      int limit) {
    Condition condition =
        AGENT_MEMORY.TENANT_ID.eq(tenantId)
            .and(AGENT_MEMORY.STATUS.eq("active"))
            .and(
                AGENT_MEMORY.EXPIRES_AT.isNull()
                    .or(AGENT_MEMORY.EXPIRES_AT.gt(OffsetDateTime.now())));

    if (scopeType != null && !scopeType.isBlank()) {
      condition = condition.and(AGENT_MEMORY.SCOPE_TYPE.eq(scopeType));
    }

    if (scopeId != null && !scopeId.isBlank()) {
      condition = condition.and(AGENT_MEMORY.SCOPE_ID.eq(scopeId));
    }

    if (memoryTypes != null && !memoryTypes.isEmpty()) {
      condition = condition.and(AGENT_MEMORY.MEMORY_TYPE.in(memoryTypes));
    }

    if (tags != null && !tags.isEmpty()) {
      for (String tag : tags) {
        condition =
            condition.and(
                DSL.lower(AGENT_MEMORY.TAGS.cast(String.class)).contains(tag.toLowerCase()));
      }
    }

    return selectMemory()
        .where(condition)
        .orderBy(AGENT_MEMORY.CONFIDENCE.desc(), AGENT_MEMORY.CREATED_AT.desc())
        .limit(Math.max(1, Math.min(limit, 200)))
        .fetch(this::toRecord);
  }

  @Override
  public boolean archive(String tenantId, String memoryId) {
    return dsl.update(AGENT_MEMORY)
            .set(AGENT_MEMORY.STATUS, "archived")
            .set(AGENT_MEMORY.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AGENT_MEMORY.TENANT_ID.eq(tenantId))
            .and(AGENT_MEMORY.ID.eq(memoryId))
            .and(AGENT_MEMORY.STATUS.eq("active"))
            .execute()
        > 0;
  }

  @Override
  public void createEvent(AgentMemoryEventCreateCommand command) {
    dsl.insertInto(AGENT_MEMORY_EVENT)
        .set(AGENT_MEMORY_EVENT.ID, command.id())
        .set(AGENT_MEMORY_EVENT.TENANT_ID, command.tenantId())
        .set(AGENT_MEMORY_EVENT.MEMORY_ID, command.memoryId())
        .set(AGENT_MEMORY_EVENT.EVENT_TYPE, command.eventType())
        .set(AGENT_MEMORY_EVENT.SUMMARY, command.summary())
        .set(AGENT_MEMORY_EVENT.ACTOR, command.actor())
        .set(AGENT_MEMORY_EVENT.METADATA, jsonbValue(command.metadataJson()))
        .set(AGENT_MEMORY_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private org.jooq.SelectJoinStep<?> selectMemory() {
    return dsl.select(
            AGENT_MEMORY.ID,
            AGENT_MEMORY.TENANT_ID,
            AGENT_MEMORY.SCOPE_TYPE,
            AGENT_MEMORY.SCOPE_ID,
            AGENT_MEMORY.MEMORY_TYPE,
            AGENT_MEMORY.SOURCE_TYPE,
            AGENT_MEMORY.SOURCE_ID,
            AGENT_MEMORY.TITLE,
            AGENT_MEMORY.CONTENT,
            AGENT_MEMORY.TAGS.cast(String.class).as("tags_json"),
            AGENT_MEMORY.CONFIDENCE,
            AGENT_MEMORY.STATUS,
            AGENT_MEMORY.CREATED_BY,
            AGENT_MEMORY.EXPIRES_AT,
            AGENT_MEMORY.CREATED_AT,
            AGENT_MEMORY.UPDATED_AT)
        .from(AGENT_MEMORY);
  }

  private AgentMemoryRecord toRecord(org.jooq.Record record) {
    return new AgentMemoryRecord(
        record.get(AGENT_MEMORY.ID),
        record.get(AGENT_MEMORY.TENANT_ID),
        record.get(AGENT_MEMORY.SCOPE_TYPE),
        record.get(AGENT_MEMORY.SCOPE_ID),
        record.get(AGENT_MEMORY.MEMORY_TYPE),
        record.get(AGENT_MEMORY.SOURCE_TYPE),
        record.get(AGENT_MEMORY.SOURCE_ID),
        record.get(AGENT_MEMORY.TITLE),
        record.get(AGENT_MEMORY.CONTENT),
        record.get("tags_json", String.class),
        doubleValue(record.get(AGENT_MEMORY.CONFIDENCE)),
        record.get(AGENT_MEMORY.STATUS),
        record.get(AGENT_MEMORY.CREATED_BY),
        record.get(AGENT_MEMORY.EXPIRES_AT),
        record.get(AGENT_MEMORY.CREATED_AT),
        record.get(AGENT_MEMORY.UPDATED_AT));
  }

  private double doubleValue(BigDecimal value) {
    return value == null ? 0.0d : value.doubleValue();
  }
}
