package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.EXECUTION_ARTIFACT;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_STEP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.ExecutionArtifactRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import org.jooq.Record;

/** Maps jOOQ records to step/artifact DTOs. */
final class ExecutionStepAndArtifactMapper {
  private ExecutionStepAndArtifactMapper() {}

  static ExecutionStepRecord toStepRecord(Record record) {
    return new ExecutionStepRecord(
        record.get(EXECUTION_STEP.ID),
        record.get(EXECUTION_STEP.TENANT_ID),
        record.get(EXECUTION_STEP.EXECUTION_ID),
        record.get(EXECUTION_STEP.PLAN_STEP_ID),
        value(record.get(EXECUTION_STEP.SEQUENCE_NO)),
        record.get(EXECUTION_STEP.NAME),
        record.get(EXECUTION_STEP.ACTION_TYPE),
        record.get(EXECUTION_STEP.TARGET_TYPE),
        record.get(EXECUTION_STEP.STATUS),
        record.get("action_payload_json", String.class),
        record.get(EXECUTION_STEP.COMMAND_SNAPSHOT),
        record.get(EXECUTION_STEP.OUTPUT),
        record.get(EXECUTION_STEP.ERROR_MESSAGE),
        record.get(EXECUTION_STEP.STARTED_AT),
        record.get(EXECUTION_STEP.FINISHED_AT),
        value(record.get(EXECUTION_STEP.ATTEMPT)),
        value(record.get(EXECUTION_STEP.TIMEOUT_SECONDS)),
        value(record.get(EXECUTION_STEP.ARTIFACT_COUNT)),
        record.get(EXECUTION_STEP.CREATED_AT),
        record.get(EXECUTION_STEP.UPDATED_AT));
  }

  static ExecutionArtifactRecord toArtifactRecord(Record record) {
    return new ExecutionArtifactRecord(
        record.get(EXECUTION_ARTIFACT.ID),
        record.get(EXECUTION_ARTIFACT.TENANT_ID),
        record.get(EXECUTION_ARTIFACT.EXECUTION_ID),
        record.get(EXECUTION_ARTIFACT.STEP_ID),
        record.get(EXECUTION_ARTIFACT.ARTIFACT_TYPE),
        record.get(EXECUTION_ARTIFACT.NAME),
        record.get(EXECUTION_ARTIFACT.CONTENT),
        serializeMetadata(record.get(EXECUTION_ARTIFACT.METADATA)),
        record.get(EXECUTION_ARTIFACT.CREATED_AT));
  }

  private static String serializeMetadata(Object jsonbValue) {
    if (jsonbValue == null) {
      return "{}";
    }
    try {
      return new ObjectMapper().writeValueAsString(jsonbValue);
    } catch (JsonProcessingException ex) {
      return "{}";
    }
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }
}
