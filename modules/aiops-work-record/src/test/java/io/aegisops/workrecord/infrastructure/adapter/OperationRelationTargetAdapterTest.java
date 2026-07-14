package io.aegisops.workrecord.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertEventRecord;
import io.aegisops.alert.AlertQueryService;
import io.aegisops.incident.IncidentService;
import io.aegisops.inspection.InspectionService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.RelationType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OperationRelationTargetAdapterTest {
  @Test
  void serializesNullableTargetSnapshotFields() {
    AlertQueryService alerts = mock(AlertQueryService.class);
    when(alerts.get("tenant-1", "alert-1"))
        .thenReturn(
            new AlertEventRecord(
                "alert-1", "tenant-1", "zabbix", null, "CPU high", "open", null, null));
    var adapter =
        new OperationRelationTargetAdapter(
            alerts, mock(IncidentService.class), mock(InspectionService.class), new ObjectMapper());

    var target = adapter.resolve("tenant-1", RelationType.ALERT, "alert-1", principal("tenant-1"));

    org.assertj.core.api.Assertions.assertThat(target.snapshotJson())
        .isEqualTo("{\"severity\":null,\"startsAt\":null}");
  }

  @Test
  void rejectsPrincipalFromAnotherTenant() {
    var adapter =
        new OperationRelationTargetAdapter(
            mock(AlertQueryService.class),
            mock(IncidentService.class),
            mock(InspectionService.class),
            new ObjectMapper());
    UserPrincipal principal = principal("tenant-2");

    assertThatThrownBy(() -> adapter.resolve("tenant-1", RelationType.ALERT, "alert-1", principal))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static UserPrincipal principal(String tenantId) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", tenantId, "alice", "Alice"),
        Set.of(),
        Set.of("alert:read"),
        Map.of());
  }
}
