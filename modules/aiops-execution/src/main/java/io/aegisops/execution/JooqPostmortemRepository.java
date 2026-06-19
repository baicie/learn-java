package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_ACTION_ITEM;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_REPORT;
import static io.aegisops.persistence.jooq.Tables.POSTMORTEM_SECTION;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPostmortemRepository implements PostmortemRepository {
  private final DSLContext dsl;

  public JooqPostmortemRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createReport(PostmortemReportCreateCommand command) {
    dsl.insertInto(POSTMORTEM_REPORT)
        .set(POSTMORTEM_REPORT.ID, command.id())
        .set(POSTMORTEM_REPORT.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_REPORT.INCIDENT_ID, command.incidentId())
        .set(POSTMORTEM_REPORT.STATUS, command.status())
        .set(POSTMORTEM_REPORT.SEVERITY, command.severity())
        .set(POSTMORTEM_REPORT.TITLE, command.title())
        .set(POSTMORTEM_REPORT.SUMMARY, command.summary())
        .set(POSTMORTEM_REPORT.IMPACT, command.impact())
        .set(POSTMORTEM_REPORT.ROOT_CAUSE, command.rootCause())
        .set(POSTMORTEM_REPORT.DETECTION, command.detection())
        .set(POSTMORTEM_REPORT.RESOLUTION, command.resolution())
        .set(POSTMORTEM_REPORT.PREVENTION, command.prevention())
        .set(POSTMORTEM_REPORT.MARKDOWN, command.markdown())
        .set(POSTMORTEM_REPORT.SOURCE_SNAPSHOT, jsonbValue(command.sourceSnapshotJson()))
        .set(POSTMORTEM_REPORT.GENERATED_BY, command.generatedBy())
        .set(POSTMORTEM_REPORT.GENERATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_REPORT.CREATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_REPORT.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createSection(PostmortemSectionCreateCommand command) {
    dsl.insertInto(POSTMORTEM_SECTION)
        .set(POSTMORTEM_SECTION.ID, command.id())
        .set(POSTMORTEM_SECTION.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_SECTION.POSTMORTEM_ID, command.postmortemId())
        .set(POSTMORTEM_SECTION.SECTION_ORDER, command.sectionOrder())
        .set(POSTMORTEM_SECTION.SECTION_TYPE, command.sectionType())
        .set(POSTMORTEM_SECTION.TITLE, command.title())
        .set(POSTMORTEM_SECTION.CONTENT, command.content())
        .set(POSTMORTEM_SECTION.METADATA, jsonbValue(command.metadataJson()))
        .set(POSTMORTEM_SECTION.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId) {
    return dsl.select(
            POSTMORTEM_REPORT.ID,
            POSTMORTEM_REPORT.TENANT_ID,
            POSTMORTEM_REPORT.INCIDENT_ID,
            POSTMORTEM_REPORT.STATUS,
            POSTMORTEM_REPORT.SEVERITY,
            POSTMORTEM_REPORT.TITLE,
            POSTMORTEM_REPORT.SUMMARY,
            POSTMORTEM_REPORT.IMPACT,
            POSTMORTEM_REPORT.ROOT_CAUSE,
            POSTMORTEM_REPORT.DETECTION,
            POSTMORTEM_REPORT.RESOLUTION,
            POSTMORTEM_REPORT.PREVENTION,
            POSTMORTEM_REPORT.MARKDOWN,
            POSTMORTEM_REPORT.SOURCE_SNAPSHOT.cast(String.class).as("source_snapshot_json"),
            POSTMORTEM_REPORT.GENERATED_BY,
            POSTMORTEM_REPORT.GENERATED_AT,
            POSTMORTEM_REPORT.REVIEWED_BY,
            POSTMORTEM_REPORT.REVIEWED_AT,
            POSTMORTEM_REPORT.ARCHIVED_AT,
            POSTMORTEM_REPORT.CREATED_AT,
            POSTMORTEM_REPORT.UPDATED_AT)
        .from(POSTMORTEM_REPORT)
        .where(POSTMORTEM_REPORT.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_REPORT.ID.eq(postmortemId))
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId) {
    return dsl.select(
            POSTMORTEM_REPORT.ID,
            POSTMORTEM_REPORT.TENANT_ID,
            POSTMORTEM_REPORT.INCIDENT_ID,
            POSTMORTEM_REPORT.STATUS,
            POSTMORTEM_REPORT.SEVERITY,
            POSTMORTEM_REPORT.TITLE,
            POSTMORTEM_REPORT.SUMMARY,
            POSTMORTEM_REPORT.IMPACT,
            POSTMORTEM_REPORT.ROOT_CAUSE,
            POSTMORTEM_REPORT.DETECTION,
            POSTMORTEM_REPORT.RESOLUTION,
            POSTMORTEM_REPORT.PREVENTION,
            POSTMORTEM_REPORT.MARKDOWN,
            POSTMORTEM_REPORT.SOURCE_SNAPSHOT.cast(String.class).as("source_snapshot_json"),
            POSTMORTEM_REPORT.GENERATED_BY,
            POSTMORTEM_REPORT.GENERATED_AT,
            POSTMORTEM_REPORT.REVIEWED_BY,
            POSTMORTEM_REPORT.REVIEWED_AT,
            POSTMORTEM_REPORT.ARCHIVED_AT,
            POSTMORTEM_REPORT.CREATED_AT,
            POSTMORTEM_REPORT.UPDATED_AT)
        .from(POSTMORTEM_REPORT)
        .where(POSTMORTEM_REPORT.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_REPORT.INCIDENT_ID.eq(incidentId))
        .orderBy(POSTMORTEM_REPORT.GENERATED_AT.desc())
        .limit(1)
        .fetchOptional(this::toReportRecord);
  }

  @Override
  public List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId) {
    return dsl.select(
            POSTMORTEM_SECTION.ID,
            POSTMORTEM_SECTION.TENANT_ID,
            POSTMORTEM_SECTION.POSTMORTEM_ID,
            POSTMORTEM_SECTION.SECTION_ORDER,
            POSTMORTEM_SECTION.SECTION_TYPE,
            POSTMORTEM_SECTION.TITLE,
            POSTMORTEM_SECTION.CONTENT,
            POSTMORTEM_SECTION.METADATA.cast(String.class).as("metadata_json"),
            POSTMORTEM_SECTION.CREATED_AT)
        .from(POSTMORTEM_SECTION)
        .where(POSTMORTEM_SECTION.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_SECTION.POSTMORTEM_ID.eq(postmortemId))
        .orderBy(POSTMORTEM_SECTION.SECTION_ORDER.asc())
        .fetch(this::toSectionRecord);
  }

  @Override
  public void createActionItem(PostmortemActionItemCreateCommand command) {
    dsl.insertInto(POSTMORTEM_ACTION_ITEM)
        .set(POSTMORTEM_ACTION_ITEM.ID, command.id())
        .set(POSTMORTEM_ACTION_ITEM.TENANT_ID, command.tenantId())
        .set(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID, command.postmortemId())
        .set(POSTMORTEM_ACTION_ITEM.TITLE, command.title())
        .set(POSTMORTEM_ACTION_ITEM.DESCRIPTION, command.description())
        .set(POSTMORTEM_ACTION_ITEM.OWNER, command.owner())
        .set(POSTMORTEM_ACTION_ITEM.PRIORITY, command.priority())
        .set(POSTMORTEM_ACTION_ITEM.STATUS, command.status())
        .set(POSTMORTEM_ACTION_ITEM.DUE_DATE, command.dueDate())
        .set(POSTMORTEM_ACTION_ITEM.SOURCE_TYPE, command.sourceType())
        .set(POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID, command.sourceRefId())
        .set(POSTMORTEM_ACTION_ITEM.CREATED_BY, command.createdBy())
        .set(POSTMORTEM_ACTION_ITEM.CREATED_AT, DSL.currentOffsetDateTime())
        .set(POSTMORTEM_ACTION_ITEM.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId) {
    return dsl.select(
            POSTMORTEM_ACTION_ITEM.ID,
            POSTMORTEM_ACTION_ITEM.TENANT_ID,
            POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID,
            POSTMORTEM_ACTION_ITEM.TITLE,
            POSTMORTEM_ACTION_ITEM.DESCRIPTION,
            POSTMORTEM_ACTION_ITEM.OWNER,
            POSTMORTEM_ACTION_ITEM.PRIORITY,
            POSTMORTEM_ACTION_ITEM.STATUS,
            POSTMORTEM_ACTION_ITEM.DUE_DATE,
            POSTMORTEM_ACTION_ITEM.SOURCE_TYPE,
            POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID,
            POSTMORTEM_ACTION_ITEM.CREATED_BY,
            POSTMORTEM_ACTION_ITEM.CREATED_AT,
            POSTMORTEM_ACTION_ITEM.UPDATED_AT)
        .from(POSTMORTEM_ACTION_ITEM)
        .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID.eq(postmortemId))
        .orderBy(POSTMORTEM_ACTION_ITEM.CREATED_AT.asc())
        .fetch(this::toActionItemRecord);
  }

  @Override
  public Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId) {
    return dsl.select(
            POSTMORTEM_ACTION_ITEM.ID,
            POSTMORTEM_ACTION_ITEM.TENANT_ID,
            POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID,
            POSTMORTEM_ACTION_ITEM.TITLE,
            POSTMORTEM_ACTION_ITEM.DESCRIPTION,
            POSTMORTEM_ACTION_ITEM.OWNER,
            POSTMORTEM_ACTION_ITEM.PRIORITY,
            POSTMORTEM_ACTION_ITEM.STATUS,
            POSTMORTEM_ACTION_ITEM.DUE_DATE,
            POSTMORTEM_ACTION_ITEM.SOURCE_TYPE,
            POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID,
            POSTMORTEM_ACTION_ITEM.CREATED_BY,
            POSTMORTEM_ACTION_ITEM.CREATED_AT,
            POSTMORTEM_ACTION_ITEM.UPDATED_AT)
        .from(POSTMORTEM_ACTION_ITEM)
        .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
        .and(POSTMORTEM_ACTION_ITEM.ID.eq(actionItemId))
        .fetchOptional(this::toActionItemRecord);
  }

  @Override
  public boolean updateActionItemStatus(String tenantId, String actionItemId, String status) {
    return dsl.update(POSTMORTEM_ACTION_ITEM)
            .set(POSTMORTEM_ACTION_ITEM.STATUS, status)
            .set(POSTMORTEM_ACTION_ITEM.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(POSTMORTEM_ACTION_ITEM.TENANT_ID.eq(tenantId))
            .and(POSTMORTEM_ACTION_ITEM.ID.eq(actionItemId))
            .execute()
        > 0;
  }

  private PostmortemReportRecord toReportRecord(Record record) {
    return new PostmortemReportRecord(
        record.get(POSTMORTEM_REPORT.ID),
        record.get(POSTMORTEM_REPORT.TENANT_ID),
        record.get(POSTMORTEM_REPORT.INCIDENT_ID),
        record.get(POSTMORTEM_REPORT.STATUS),
        record.get(POSTMORTEM_REPORT.SEVERITY),
        record.get(POSTMORTEM_REPORT.TITLE),
        record.get(POSTMORTEM_REPORT.SUMMARY),
        record.get(POSTMORTEM_REPORT.IMPACT),
        record.get(POSTMORTEM_REPORT.ROOT_CAUSE),
        record.get(POSTMORTEM_REPORT.DETECTION),
        record.get(POSTMORTEM_REPORT.RESOLUTION),
        record.get(POSTMORTEM_REPORT.PREVENTION),
        record.get(POSTMORTEM_REPORT.MARKDOWN),
        record.get("source_snapshot_json", String.class),
        record.get(POSTMORTEM_REPORT.GENERATED_BY),
        record.get(POSTMORTEM_REPORT.GENERATED_AT),
        record.get(POSTMORTEM_REPORT.REVIEWED_BY),
        record.get(POSTMORTEM_REPORT.REVIEWED_AT),
        record.get(POSTMORTEM_REPORT.ARCHIVED_AT),
        record.get(POSTMORTEM_REPORT.CREATED_AT),
        record.get(POSTMORTEM_REPORT.UPDATED_AT));
  }

  private PostmortemSectionRecord toSectionRecord(Record record) {
    return new PostmortemSectionRecord(
        record.get(POSTMORTEM_SECTION.ID),
        record.get(POSTMORTEM_SECTION.TENANT_ID),
        record.get(POSTMORTEM_SECTION.POSTMORTEM_ID),
        value(record.get(POSTMORTEM_SECTION.SECTION_ORDER)),
        record.get(POSTMORTEM_SECTION.SECTION_TYPE),
        record.get(POSTMORTEM_SECTION.TITLE),
        record.get(POSTMORTEM_SECTION.CONTENT),
        record.get("metadata_json", String.class),
        record.get(POSTMORTEM_SECTION.CREATED_AT));
  }

  private PostmortemActionItemRecord toActionItemRecord(Record record) {
    return new PostmortemActionItemRecord(
        record.get(POSTMORTEM_ACTION_ITEM.ID),
        record.get(POSTMORTEM_ACTION_ITEM.TENANT_ID),
        record.get(POSTMORTEM_ACTION_ITEM.POSTMORTEM_ID),
        record.get(POSTMORTEM_ACTION_ITEM.TITLE),
        record.get(POSTMORTEM_ACTION_ITEM.DESCRIPTION),
        record.get(POSTMORTEM_ACTION_ITEM.OWNER),
        record.get(POSTMORTEM_ACTION_ITEM.PRIORITY),
        record.get(POSTMORTEM_ACTION_ITEM.STATUS),
        record.get(POSTMORTEM_ACTION_ITEM.DUE_DATE),
        record.get(POSTMORTEM_ACTION_ITEM.SOURCE_TYPE),
        record.get(POSTMORTEM_ACTION_ITEM.SOURCE_REF_ID),
        record.get(POSTMORTEM_ACTION_ITEM.CREATED_BY),
        record.get(POSTMORTEM_ACTION_ITEM.CREATED_AT),
        record.get(POSTMORTEM_ACTION_ITEM.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
