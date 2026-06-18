package io.aegisops.execution;

import io.aegisops.execution.dto.WebhookConnectorCreateCommand;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import io.aegisops.execution.dto.WebhookPolicyCreateCommand;
import io.aegisops.execution.dto.WebhookPolicyRecord;
import java.util.List;
import java.util.Optional;

public interface WebhookRepository {
  void createConnector(WebhookConnectorCreateCommand command);

  void createPolicy(WebhookPolicyCreateCommand command);

  List<WebhookConnectorRecord> listConnectors(String tenantId, boolean includeDisabled);

  Optional<WebhookConnectorRecord> findConnector(String tenantId, String connectorId);

  Optional<WebhookPolicyRecord> findPolicy(String tenantId, String connectorId);

  boolean setConnectorEnabled(String tenantId, String connectorId, boolean enabled);
}
