package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.service.ExcelImportProcessor;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExcelImportJobTest {
  private final ExcelImportProcessor processor = mock(ExcelImportProcessor.class);
  private final UserService users = mock(UserService.class);
  private final UserPrincipalFactory principals = mock(UserPrincipalFactory.class);
  private final ExcelImportJob job =
      new ExcelImportJob(processor, users, principals, new ObjectMapper());

  @Test
  void reloadsCurrentUserAuthorizationBeforeExecuting() {
    AutomationOutboxRecord row = mock(AutomationOutboxRecord.class);
    when(row.getTenantId()).thenReturn("tenant-1");
    when(row.getPayload())
        .thenReturn(
            org.jooq.JSONB.jsonb(
                "{\"tenantId\":\"tenant-1\",\"jobId\":\"job-1\",\"requestedBy\":\"user-1\"}"));
    UserAccount account =
        new UserAccount(
            "user-1",
            "tenant-1",
            "alice",
            "Alice",
            null,
            "hash",
            "active",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(users.getById("user-1")).thenReturn(account);
    var principal =
        new io.aegisops.security.UserPrincipal(
            new io.aegisops.security.UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
            java.util.Set.of(),
            java.util.Set.of("work-record:import"),
            java.util.Map.of());
    when(principals.create(account)).thenReturn(principal);

    assertThat(job.handle(row).isSuccess()).isTrue();

    verify(processor).process("tenant-1", "job-1", principal);
  }
}
