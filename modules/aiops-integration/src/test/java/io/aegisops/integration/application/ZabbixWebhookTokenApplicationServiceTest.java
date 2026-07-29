package io.aegisops.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.AppException;
import io.aegisops.integration.application.port.ZabbixWebhookDatasourceStore;
import io.aegisops.integration.zabbix.ZabbixWebhookTokenVerifier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZabbixWebhookTokenApplicationServiceTest {
  private final ZabbixWebhookDatasourceStore datasourceStore =
      mock(ZabbixWebhookDatasourceStore.class);
  private final ZabbixWebhookTokenVerifier tokenVerifier = mock(ZabbixWebhookTokenVerifier.class);
  private final AuditService auditService = mock(AuditService.class);
  private final ZabbixWebhookTokenApplicationService service =
      new ZabbixWebhookTokenApplicationService(datasourceStore, tokenVerifier, auditService);

  @Test
  void derivesTokenOnlyForTenantOwnedZabbixDatasource() {
    when(datasourceStore.existsZabbix("tenant-a", "ds-a")).thenReturn(true);
    when(tokenVerifier.tokenForDatasource("ds-a")).thenReturn("zwh_datasource-token");

    assertThat(
            service.issueToken(
                "tenant-a",
                "ds-a",
                "operator-a",
                "req-token-1",
                "203.0.113.10",
                "portal-test-agent"))
        .isEqualTo("zwh_datasource-token");

    verify(datasourceStore).existsZabbix("tenant-a", "ds-a");
    verify(tokenVerifier).tokenForDatasource("ds-a");
    ArgumentCaptor<AuditRecordCommand> auditCaptor =
        ArgumentCaptor.forClass(AuditRecordCommand.class);
    verify(auditService).record(auditCaptor.capture());
    AuditRecordCommand audit = auditCaptor.getValue();
    assertThat(audit.tenantId()).isEqualTo("tenant-a");
    assertThat(audit.actorId()).isEqualTo("operator-a");
    assertThat(audit.action()).isEqualTo("datasource.zabbix_webhook_token.issue");
    assertThat(audit.resourceType()).isEqualTo("datasource");
    assertThat(audit.resourceId()).isEqualTo("ds-a");
    assertThat(audit.beforeJson()).isEqualTo("{}");
    assertThat(audit.afterJson()).isEqualTo("{}");
    assertThat(audit.detailJson()).isEqualTo("{}");
    assertThat(audit.requestId()).isEqualTo("req-token-1");
    assertThat(audit.ip()).isEqualTo("203.0.113.10");
    assertThat(audit.userAgent()).isEqualTo("portal-test-agent");
    assertThat(audit.toString()).doesNotContain("zwh_datasource-token");
  }

  @Test
  void rejectsForeignTenantOrNonZabbixDatasourceWithoutDerivingToken() {
    when(datasourceStore.existsZabbix("tenant-a", "ds-b")).thenReturn(false);

    assertThatThrownBy(() -> service.issueToken("tenant-a", "ds-b", "operator-a"))
        .isInstanceOfSatisfying(
            AppException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo("DATASOURCE_NOT_FOUND"));

    verifyNoInteractions(tokenVerifier);
    verifyNoInteractions(auditService);
  }
}
