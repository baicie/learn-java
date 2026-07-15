package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.StatisticsQuery;
import io.aegisops.workrecord.application.port.StatisticsRepository;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordStatisticsServiceTest {
  @Test
  void rejectsMissingAnalyticsPermissionBeforeQueryingData() {
    StatisticsRepository statistics = mock(StatisticsRepository.class);
    WorkRecordFieldIndexRepository fields = mock(WorkRecordFieldIndexRepository.class);
    WorkRecordCalendarPort calendar = mock(WorkRecordCalendarPort.class);
    FieldPolicyService fieldPolicies = mock(FieldPolicyService.class);
    var service = new WorkRecordStatisticsService(statistics, fields, calendar, fieldPolicies);

    assertThatThrownBy(() -> service.statistics("tenant-1", query(), principal(Set.of())))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(statistics, fields, calendar, fieldPolicies);
  }

  @Test
  void rejectsTenantWideAnalyticsForSelfOnlyReader() {
    StatisticsRepository statistics = mock(StatisticsRepository.class);
    WorkRecordFieldIndexRepository fields = mock(WorkRecordFieldIndexRepository.class);
    WorkRecordCalendarPort calendar = mock(WorkRecordCalendarPort.class);
    FieldPolicyService fieldPolicies = mock(FieldPolicyService.class);
    var service = new WorkRecordStatisticsService(statistics, fields, calendar, fieldPolicies);

    assertThatThrownBy(
            () ->
                service.statistics(
                    "tenant-1",
                    query(),
                    principal(
                        Set.of(
                            "work-record:analytics",
                            io.aegisops.security.PermissionCodes.WORK_RECORD_READ_SELF))))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(statistics, fields, calendar, fieldPolicies);
  }

  private static StatisticsQuery query() {
    return new StatisticsQuery(
        null,
        null,
        OffsetDateTime.parse("2026-07-01T00:00:00Z"),
        OffsetDateTime.parse("2026-08-01T00:00:00Z"),
        "day",
        null);
  }

  private static UserPrincipal principal(Set<String> permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        permissions,
        Map.of());
  }
}
