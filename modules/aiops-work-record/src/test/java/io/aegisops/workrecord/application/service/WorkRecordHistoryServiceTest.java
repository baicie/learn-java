package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.audit.AuditEvent;
import io.aegisops.audit.AuditJson;
import io.aegisops.audit.AuditQueryService;
import io.aegisops.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordHistoryServiceTest {
  @Test
  void rebuildsHistoryDiffFromFieldFilteredSnapshots() {
    AuditQueryService query = mock(AuditQueryService.class);
    FieldPolicyService policies = mock(FieldPolicyService.class);
    ObjectMapper objectMapper = new ObjectMapper();
    UserPrincipal principal =
        new UserPrincipal(
            new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
            Set.of("normal_user"),
            Set.of("work-record:read:self"),
            Map.of());
    AuditEvent event =
        new AuditEvent(
            "audit-1",
            "tenant-1",
            "user-1",
            "work_record.record.update",
            "work_record",
            "record-1",
            "{\"customData\":{\"secret\":\"before\"}}",
            "{\"customData\":{\"secret\":\"after\"}}",
            "{\"requestId\":\"req-1\",\"changes\":[{\"path\":\"/customData/secret\"}]}",
            OffsetDateTime.parse("2026-07-15T00:00:00Z"));
    when(query.listByResource("tenant-1", "work_record", "record-1", 100))
        .thenReturn(List.of(event));
    when(policies.filterAuditSnapshot("tenant-1", "version-1", event.beforeJson(), principal))
        .thenReturn("{\"customData\":{}}");
    when(policies.filterAuditSnapshot("tenant-1", "version-1", event.afterJson(), principal))
        .thenReturn("{\"customData\":{}}");

    var service =
        new WorkRecordHistoryService(query, new AuditJson(objectMapper), policies, objectMapper);
    AuditEvent filtered = service.list("tenant-1", "record-1", "version-1", principal).getFirst();

    assertThat(filtered.beforeJson()).doesNotContain("secret");
    assertThat(filtered.afterJson()).doesNotContain("secret");
    assertThat(filtered.detailJson()).contains("req-1", "\"changes\":[]").doesNotContain("secret");
  }
}
