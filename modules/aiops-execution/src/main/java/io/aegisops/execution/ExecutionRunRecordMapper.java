package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;

import io.aegisops.execution.dto.ExecutionRunRecord;
import java.util.function.IntFunction;
import org.jooq.Record;

/** Maps jOOQ records to {@link ExecutionRunRecord}. */
final class ExecutionRunRecordMapper {
  private ExecutionRunRecordMapper() {}

  static ExecutionRunRecord toRunRecord(Record record, IntFunction<Integer> nullSafe) {
    return new ExecutionRunRecord(
        record.get(EXECUTION_RUN.ID),
        record.get(EXECUTION_RUN.TENANT_ID),
        record.get(EXECUTION_RUN.INCIDENT_ID),
        record.get(EXECUTION_RUN.PLAN_ID),
        record.get(EXECUTION_RUN.STATUS),
        record.get(EXECUTION_RUN.MODE),
        record.get(EXECUTION_RUN.REQUESTED_BY),
        record.get(EXECUTION_RUN.RUNNER_ID),
        record.get(EXECUTION_RUN.STARTED_AT),
        record.get(EXECUTION_RUN.FINISHED_AT),
        record.get(EXECUTION_RUN.ERROR_MESSAGE),
        record.get(EXECUTION_RUN.SUMMARY),
        nullSafe.apply(record.get(EXECUTION_RUN.ATTEMPT)),
        nullSafe.apply(record.get(EXECUTION_RUN.MAX_ATTEMPTS)),
        record.get(EXECUTION_RUN.RETRY_OF_EXECUTION_ID),
        record.get(EXECUTION_RUN.LEASE_UNTIL),
        record.get(EXECUTION_RUN.HEARTBEAT_AT),
        nullSafe.apply(record.get(EXECUTION_RUN.TIMEOUT_SECONDS)),
        record.get(EXECUTION_RUN.APPROVAL_ID),
        record.get("approval_snapshot_json", String.class),
        record.get(EXECUTION_RUN.PLAN_RISK_LEVEL),
        record.get(EXECUTION_RUN.LIVE_GUARD_PASSED_AT),
        record.get(EXECUTION_RUN.EXECUTION_KIND),
        record.get(EXECUTION_RUN.ROLLBACK_PLAN_ID),
        record.get(EXECUTION_RUN.ROLLBACK_OF_EXECUTION_ID),
        record.get(EXECUTION_RUN.CREATED_AT),
        record.get(EXECUTION_RUN.UPDATED_AT));
  }
}
