package io.aegisops.server.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.PlatformModule;
import io.aegisops.platform.PlatformModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WorkbenchSummaryServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneId.of("UTC"));

  @Mock private JdbcTemplate jdbc;

  @Mock private PlatformModuleRepository moduleRepository;

  @Test
  void summary_shouldReturnZerosWhenNoData() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(0L);
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString(), any(OffsetDateTime.class)))
        .thenReturn(0L);
    when(moduleRepository.findAll()).thenReturn(List.of());

    WorkbenchSummaryService service =
        new WorkbenchSummaryService(jdbc, moduleRepository, FIXED_CLOCK);

    WorkbenchSummary result = service.summary("tenant-1");

    assertThat(result.activeIncidents()).isZero();
    assertThat(result.criticalAlerts()).isZero();
    assertThat(result.todayNewAlerts()).isZero();
    assertThat(result.datasourceErrors()).isZero();
    assertThat(result.pendingTasks()).isZero();
    assertThat(result.moduleHealth()).isEqualTo("HEALTHY");
    verify(jdbc, never()).queryForObject(contains("automation_job"), eq(Long.class), anyString());
  }

  @Test
  void summary_shouldReturnUnhealthyWhenModuleUnhealthy() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString())).thenReturn(0L);
    when(jdbc.queryForObject(anyString(), eq(Long.class), anyString(), any(OffsetDateTime.class)))
        .thenReturn(0L);
    PlatformModule unhealthy =
        new PlatformModule("1", "zabbix", "Zabbix", "1.0.0", true, "UNHEALTHY", "{}", null);
    when(moduleRepository.findAll()).thenReturn(List.of(unhealthy));

    WorkbenchSummaryService service =
        new WorkbenchSummaryService(jdbc, moduleRepository, FIXED_CLOCK);

    WorkbenchSummary result = service.summary("tenant-1");

    assertThat(result.moduleHealth()).isEqualTo("UNHEALTHY");
  }
}
