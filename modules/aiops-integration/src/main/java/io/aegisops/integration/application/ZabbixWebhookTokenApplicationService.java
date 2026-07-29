package io.aegisops.integration.application;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.AppException;
import io.aegisops.integration.application.port.ZabbixWebhookDatasourceStore;
import io.aegisops.integration.zabbix.ZabbixWebhookTokenVerifier;
import org.springframework.stereotype.Service;

@Service
public class ZabbixWebhookTokenApplicationService {
  private static final String AUDIT_ACTION = "datasource.zabbix_webhook_token.issue";

  private final ZabbixWebhookDatasourceStore datasourceStore;
  private final ZabbixWebhookTokenVerifier tokenVerifier;
  private final AuditService auditService;

  public ZabbixWebhookTokenApplicationService(
      ZabbixWebhookDatasourceStore datasourceStore,
      ZabbixWebhookTokenVerifier tokenVerifier,
      AuditService auditService) {
    this.datasourceStore = datasourceStore;
    this.tokenVerifier = tokenVerifier;
    this.auditService = auditService;
  }

  public String issueToken(String tenantId, String datasourceId, String actorId) {
    return issueToken(tenantId, datasourceId, actorId, null, null, null);
  }

  public String issueToken(
      String tenantId,
      String datasourceId,
      String actorId,
      String requestId,
      String ip,
      String userAgent) {
    if (!datasourceStore.existsZabbix(tenantId, datasourceId)) {
      throw new AppException("DATASOURCE_NOT_FOUND", "Zabbix datasource not found");
    }
    String token = tokenVerifier.tokenForDatasource(datasourceId);
    auditService.record(
        new AuditRecordCommand(
            tenantId,
            actorId,
            AUDIT_ACTION,
            "datasource",
            datasourceId,
            "{}",
            "{}",
            "{}",
            requestId,
            ip,
            userAgent));
    return token;
  }
}
