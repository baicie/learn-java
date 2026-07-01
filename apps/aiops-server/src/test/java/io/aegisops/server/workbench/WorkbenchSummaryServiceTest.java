package io.aegisops.server.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.aegisops.platform.PlatformModule;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WorkbenchSummaryServiceTest {
  @Mock
  private JdbcTemplate jdbc;

  @Mock
  private io.aegisops.platform.PlatformModuleRepository moduleRepository;

  @Test
  void summary_shouldReturnZerosWhenNoData() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
    when(moduleRepository.findAll()).thenReturn(Collections.emptyList());

    WorkbenchSummaryService service = new WorkbenchSummaryService(jdbc, moduleRepository);
    WorkbenchSummary result = service.summary("tenant-1");

    assertThat(result.activeIncidents()).isZero();
    assertThat(result.moduleHealth()).isEqualTo("HEALTHY");
  }

  @Test
  void summary_shouldReturnUnhealthyWhenModuleUnhealthy() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
    PlatformModule unhealthy =
        new PlatformModule("1", "zabbix", "Zabbix", "1.0.0", true, "UNHEALTHY", "{}", null);
    when(moduleRepository.findAll()).thenReturn(Collections.singletonList(unhealthy));

    WorkbenchSummaryService service = new WorkbenchSummaryService(jdbc, moduleRepository);
    WorkbenchSummary result = service.summary("tenant-1");

    assertThat(result.moduleHealth()).isEqualTo("UNHEALTHY");
  }
}
