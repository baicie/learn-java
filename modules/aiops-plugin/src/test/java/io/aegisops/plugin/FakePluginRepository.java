package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.PluginEventCommand;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Thread-unsafe fake repository for PluginService unit tests. */
class FakePluginRepository implements PluginRepository {
  final Map<String, PluginDescriptorRecord> plugins = new HashMap<>();
  final Map<String, TenantPluginRecord> tenantPlugins = new HashMap<>();
  final List<TenantPluginToolPolicyRecord> policies = new ArrayList<>();
  final List<PluginEventCommand> events = new ArrayList<>();

  @Override
  public void upsertDescriptor(PluginDescriptorCreateCommand command) {
    plugins.put(
        command.id(),
        new PluginDescriptorRecord(
            command.id(),
            command.pluginKey(),
            command.name(),
            command.version(),
            command.description(),
            command.provider(),
            command.status(),
            command.manifestJson(),
            command.capabilitiesJson(),
            command.createdBy(),
            OffsetDateTime.now(),
            OffsetDateTime.now()));
  }

  @Override
  public Optional<PluginDescriptorRecord> findPlugin(String pluginId) {
    return Optional.ofNullable(plugins.get(pluginId));
  }

  @Override
  public Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version) {
    return plugins.values().stream()
        .filter(item -> item.pluginKey().equals(pluginKey) && item.version().equals(version))
        .findFirst();
  }

  @Override
  public List<PluginDescriptorRecord> listPlugins() {
    return new ArrayList<>(plugins.values());
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId) {
    return Optional.ofNullable(tenantPlugins.get(tenantPluginId))
        .filter(item -> item.tenantId().equals(tenantId));
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId) {
    return tenantPlugins.values().stream()
        .filter(item -> item.tenantId().equals(tenantId) && item.pluginId().equals(pluginId))
        .findFirst();
  }

  @Override
  public List<TenantPluginRecord> listTenantPlugins(String tenantId) {
    return tenantPlugins.values().stream()
        .filter(item -> item.tenantId().equals(tenantId))
        .toList();
  }

  @Override
  public String enablePlugin(
      String tenantId, String pluginId, String configJson, String enabledBy) {
    PluginDescriptorRecord plugin = plugins.get(pluginId);
    String id = "tplg_" + UUID.randomUUID().toString().replace("-", "");
    tenantPlugins.put(
        id,
        new TenantPluginRecord(
            id,
            tenantId,
            pluginId,
            plugin.pluginKey(),
            plugin.name(),
            plugin.version(),
            "enabled",
            configJson,
            plugin.manifestJson(),
            plugin.capabilitiesJson(),
            enabledBy,
            OffsetDateTime.now(),
            null,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now()));
    return id;
  }

  @Override
  public boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy) {
    TenantPluginRecord existing = tenantPlugins.get(tenantPluginId);
    if (existing == null || !existing.tenantId().equals(tenantId)) {
      return false;
    }
    tenantPlugins.put(
        tenantPluginId,
        new TenantPluginRecord(
            existing.id(),
            existing.tenantId(),
            existing.pluginId(),
            existing.pluginKey(),
            existing.name(),
            existing.version(),
            "disabled",
            existing.configJson(),
            existing.manifestJson(),
            existing.capabilitiesJson(),
            existing.enabledBy(),
            existing.enabledAt(),
            disabledBy,
            OffsetDateTime.now(),
            existing.createdAt(),
            OffsetDateTime.now()));
    return true;
  }

  @Override
  public List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId) {
    return policies.stream().filter(item -> item.tenantId().equals(tenantId)).toList();
  }

  @Override
  public Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(
      String tenantId, String toolKey) {
    return policies.stream()
        .filter(item -> item.tenantId().equals(tenantId))
        .filter(item -> item.toolKey().equals(toolKey))
        .filter(item -> item.status().equals("allowed"))
        .findFirst();
  }

  @Override
  public void upsertToolPolicy(ToolPolicyCommand cmd) {
    policies.removeIf(
        p -> p.tenantPluginId().equals(cmd.tenantPluginId()) && p.toolKey().equals(cmd.toolKey()));
    PluginDescriptorRecord plugin = plugins.get(cmd.pluginId());
    policies.add(
        new TenantPluginToolPolicyRecord(
            cmd.id(),
            cmd.tenantId(),
            cmd.tenantPluginId(),
            cmd.pluginId(),
            plugin != null ? plugin.pluginKey() : "unknown",
            cmd.toolKey(),
            cmd.status(),
            cmd.riskLevel(),
            cmd.createdBy(),
            OffsetDateTime.now(),
            OffsetDateTime.now()));
  }

  @Override
  public void createEvent(PluginEventCommand cmd) {
    events.add(cmd);
  }
}
