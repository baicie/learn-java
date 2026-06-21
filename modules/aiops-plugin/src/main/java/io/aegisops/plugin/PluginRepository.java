package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.PluginEventCommand;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.util.List;
import java.util.Optional;

public interface PluginRepository {
  void upsertDescriptor(PluginDescriptorCreateCommand command);

  Optional<PluginDescriptorRecord> findPlugin(String pluginId);

  Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version);

  List<PluginDescriptorRecord> listPlugins();

  Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId);

  Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId);

  List<TenantPluginRecord> listTenantPlugins(String tenantId);

  String enablePlugin(String tenantId, String pluginId, String configJson, String enabledBy);

  boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy);

  List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId);

  Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(String tenantId, String toolKey);

  void upsertToolPolicy(ToolPolicyCommand command);

  void createEvent(PluginEventCommand command);
}
