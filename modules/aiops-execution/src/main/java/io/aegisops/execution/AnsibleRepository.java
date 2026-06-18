package io.aegisops.execution;

import io.aegisops.execution.dto.AnsibleCredentialCreateCommand;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import java.util.List;
import java.util.Optional;

public interface AnsibleRepository {
  void createInventory(AnsibleInventoryCreateCommand command);

  List<AnsibleInventoryRecord> listInventories(String tenantId, boolean includeDisabled);

  Optional<AnsibleInventoryRecord> findInventory(String tenantId, String inventoryId);

  boolean setInventoryEnabled(String tenantId, String inventoryId, boolean enabled);

  void createPlaybook(AnsiblePlaybookCreateCommand command);

  void createPolicy(AnsiblePolicyCreateCommand command);

  List<AnsiblePlaybookRecord> listPlaybooks(String tenantId, boolean includeDisabled);

  Optional<AnsiblePlaybookRecord> findPlaybook(String tenantId, String playbookId);

  Optional<AnsiblePolicyRecord> findPolicy(String tenantId, String playbookId);

  boolean setPlaybookEnabled(String tenantId, String playbookId, boolean enabled);

  void createCredential(AnsibleCredentialCreateCommand command);

  List<AnsibleCredentialRecord> listCredentials(String tenantId, boolean includeDisabled);

  Optional<AnsibleCredentialRecord> findCredential(String tenantId, String credentialId);

  boolean setCredentialEnabled(String tenantId, String credentialId, boolean enabled);
}
