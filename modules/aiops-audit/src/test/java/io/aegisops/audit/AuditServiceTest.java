package io.aegisops.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
  @Mock
  private JdbcTemplate jdbc;

  @Test
  void record_shouldInsertAuditLog() {
    AuditService service = new AuditService(jdbc);
    AuditRecordCommand cmd =
        new AuditRecordCommand(
            "tenant-1",
            "user-1",
            "module.view",
            "MODULE",
            "platform",
            "{}");

    service.record(cmd);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sqlCaptor.capture(), any(), any(), any(), any(), any(), any());
    assertThat(sqlCaptor.getValue()).contains("insert into audit_log");
  }
}
