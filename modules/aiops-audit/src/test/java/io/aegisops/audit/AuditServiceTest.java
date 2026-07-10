package io.aegisops.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
  @Mock private NamedParameterJdbcTemplate jdbc;

  @Test
  void recordShouldInsertAuditLog() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand("tenant-1", "user-1", "module.view", "MODULE", "platform", "{}");

    service.record(cmd);

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sqlCaptor.capture(), any(Map.class));
    assertThat(sqlCaptor.getValue()).contains("insert into public.audit_log");
  }

  @Test
  void recordShouldNormalizeBeforeAndAfterJson() throws Exception {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand(
            "tenant-1",
            "user-1",
            "module.view",
            "MODULE",
            "platform",
            "{\"a\":1}",
            "{\"b\":2}",
            "{\"c\":3}");

    AuditEvent event = service.record(cmd);

    assertThat(event.beforeJson()).contains("\"a\":1");
    assertThat(event.afterJson()).contains("\"b\":2");
    assertThat(event.detailJson()).contains("\"c\":3");
  }

  @Test
  void recordShouldUseSystemWhenActorIsBlank() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand("tenant-1", "  ", "module.view", "MODULE", "platform", "{}");

    AuditEvent event = service.record(cmd);

    assertThat(event.actorId()).isEqualTo("system");
  }

  @Test
  void recordShouldRejectMissingTenant() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand(null, "user-1", "module.view", "MODULE", "platform", "{}");

    assertThatThrownBy(() -> service.record(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void recordShouldRejectMissingAction() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand("t1", "user-1", "  ", "MODULE", "platform", "{}");

    assertThatThrownBy(() -> service.record(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action");
  }

  @Test
  void recordShouldRejectBeforeJsonThatIsNotObject() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand(
            "tenant-1", "user-1", "module.view", "MODULE", "platform", "[]", "{}", "{}");

    assertThatThrownBy(() -> service.record(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("beforeJson");
  }

  @Test
  void recordShouldRejectAfterJsonThatIsNotObject() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand(
            "tenant-1", "user-1", "module.view", "MODULE", "platform", "{}", "\"oops\"", "{}");

    assertThatThrownBy(() -> service.record(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("afterJson");
  }

  @Test
  void recordShouldRejectDetailJsonThatIsNotObject() {
    AuditRepository repository = new AuditRepository(jdbc);
    AuditJson auditJson = new AuditJson(new com.fasterxml.jackson.databind.ObjectMapper());
    AuditService service = new AuditService(repository, auditJson);
    AuditRecordCommand cmd =
        new AuditRecordCommand(
            "tenant-1", "user-1", "module.view", "MODULE", "platform", "{}", "{}", "[]");

    assertThatThrownBy(() -> service.record(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("detailJson");
  }
}
