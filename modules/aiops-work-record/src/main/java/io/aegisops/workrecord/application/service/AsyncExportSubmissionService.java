package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.AsyncExportJobRequest;
import io.aegisops.workrecord.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AsyncExportSubmissionService {
  public static final int MAX_ROWS = 100_000;

  private final AsyncJobService jobs;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AsyncExportSubmissionService(
      AsyncJobService jobs, ObjectMapper objectMapper, @Qualifier("workRecordClock") Clock clock) {
    this.jobs = jobs;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public AsyncJob submit(
      String tenantId, RecordQuery query, List<String> columns, UserPrincipal principal) {
    requirePrincipal(tenantId, principal);
    if (query == null) {
      throw new IllegalArgumentException("export query is required");
    }
    try {
      String request =
          objectMapper.writeValueAsString(
              new AsyncExportJobRequest(query, columns == null ? List.of() : columns, MAX_ROWS));
      return jobs.create(
          tenantId,
          principal.id(),
          new CreateAsyncJobCommand(
              AsyncJobType.EXCEL_EXPORT,
              request,
              null,
              OffsetDateTime.now(clock).plusDays(7),
              "work-record-async-export"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid export request", ex);
    }
  }

  private static void requirePrincipal(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_EXPORT_ASYNC)
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_EXPORT)) {
      throw new AccessDeniedException("not allowed to submit asynchronous exports");
    }
  }
}
