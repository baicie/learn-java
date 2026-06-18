package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_ARTIFACT;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_STEP;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * jOOQ queries for {@code execution_artifact} and step artifact count.
 *
 * <p>Extracted from {@link JooqExecutionRepository} to keep the primary repository within the
 * configured file length budget while keeping artifact persistence in the same module.
 */
final class JooqExecutionArtifactQueries {

  private final DSLContext dsl;

  JooqExecutionArtifactQueries(DSLContext dsl) {
    this.dsl = dsl;
  }

  List<ExecutionArtifactRecord> listArtifacts(String tenantId, String executionId) {
    return dsl.select(
            EXECUTION_ARTIFACT.ID,
            EXECUTION_ARTIFACT.TENANT_ID,
            EXECUTION_ARTIFACT.EXECUTION_ID,
            EXECUTION_ARTIFACT.STEP_ID,
            EXECUTION_ARTIFACT.ARTIFACT_TYPE,
            EXECUTION_ARTIFACT.NAME,
            EXECUTION_ARTIFACT.CONTENT,
            EXECUTION_ARTIFACT.METADATA.cast(String.class).as("metadata_json"),
            EXECUTION_ARTIFACT.CREATED_AT)
        .from(EXECUTION_ARTIFACT)
        .where(EXECUTION_ARTIFACT.TENANT_ID.eq(tenantId))
        .and(EXECUTION_ARTIFACT.EXECUTION_ID.eq(executionId))
        .orderBy(EXECUTION_ARTIFACT.CREATED_AT.asc())
        .fetch(
            record ->
                new ExecutionArtifactRecord(
                    record.get(EXECUTION_ARTIFACT.ID),
                    record.get(EXECUTION_ARTIFACT.TENANT_ID),
                    record.get(EXECUTION_ARTIFACT.EXECUTION_ID),
                    record.get(EXECUTION_ARTIFACT.STEP_ID),
                    record.get(EXECUTION_ARTIFACT.ARTIFACT_TYPE),
                    record.get(EXECUTION_ARTIFACT.NAME),
                    record.get(EXECUTION_ARTIFACT.CONTENT),
                    record.get("metadata_json", String.class),
                    record.get(EXECUTION_ARTIFACT.CREATED_AT)));
  }

  void createArtifact(ExecutionArtifactCreateCommand command) {
    dsl.insertInto(EXECUTION_ARTIFACT)
        .set(EXECUTION_ARTIFACT.ID, command.id())
        .set(EXECUTION_ARTIFACT.TENANT_ID, command.tenantId())
        .set(EXECUTION_ARTIFACT.EXECUTION_ID, command.executionId())
        .set(EXECUTION_ARTIFACT.STEP_ID, command.stepId())
        .set(EXECUTION_ARTIFACT.ARTIFACT_TYPE, command.artifactType())
        .set(EXECUTION_ARTIFACT.NAME, command.name())
        .set(EXECUTION_ARTIFACT.CONTENT, command.content())
        .set(EXECUTION_ARTIFACT.METADATA, jsonbValue(command.metadataJson()))
        .set(EXECUTION_ARTIFACT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return dsl.update(EXECUTION_STEP)
            .set(EXECUTION_STEP.ARTIFACT_COUNT, EXECUTION_STEP.ARTIFACT_COUNT.plus(1))
            .set(EXECUTION_STEP.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(EXECUTION_STEP.TENANT_ID.eq(tenantId))
            .and(EXECUTION_STEP.ID.eq(stepId))
            .execute()
        > 0;
  }
}
