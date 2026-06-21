package io.aegisops.plugin;

import io.aegisops.common.exception.AppException;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.AgentToolAuthorizeResponse;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.PluginDescriptorResponse;
import io.aegisops.plugin.dto.PluginDisableRequest;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.PluginEventCommand;
import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import io.aegisops.plugin.dto.TenantFrontendManifestResponse;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginResponse;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyResponse;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginService {
  private final PluginRepository repository;
  private final PluginExtensionPointRegistry extensionPointRegistry;
  private final PluginManifestValidator validator;
  private final PluginJson json;

  public PluginService(
      PluginRepository repository,
      PluginExtensionPointRegistry extensionPointRegistry,
      PluginManifestValidator validator,
      PluginJson json) {
    this.repository = repository;
    this.extensionPointRegistry = extensionPointRegistry;
    this.validator = validator;
    this.json = json;
  }

  public List<PluginExtensionPointResponse> listExtensionPoints() {
    return extensionPointRegistry.list();
  }

  public List<PluginDescriptorResponse> listPlugins() {
    return repository.listPlugins().stream().map(this::toPluginResponse).toList();
  }

  public PluginDescriptorResponse getPlugin(String pluginId) {
    return toPluginResponse(loadPlugin(pluginId));
  }

  public List<TenantPluginResponse> listTenantPlugins(String tenantId) {
    return repository.listTenantPlugins(tenantId).stream()
        .map(this::toTenantPluginResponse)
        .toList();
  }

  @Transactional
  public TenantPluginResponse enablePlugin(
      String tenantId, String pluginId, PluginEnableRequest request) {
    PluginDescriptorRecord plugin = loadPlugin(pluginId);
    ensurePluginActive(plugin);

    validator.validateManifest(plugin.manifestJson());

    String enabledBy = blankToDefault(request == null ? null : request.enabledBy(), "system");
    String configJson = normalizeConfigJson(request == null ? null : request.configJson());

    String tenantPluginId = repository.enablePlugin(tenantId, pluginId, configJson, enabledBy);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);

    allowDefaultTools(tenantId, tenantPlugin, plugin, enabledBy);

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            tenantId,
            plugin.id(),
            tenantPlugin.id(),
            "plugin_enabled",
            "Plugin enabled for tenant",
            enabledBy,
            "{}"));

    return toTenantPluginResponse(tenantPlugin);
  }

  @Transactional
  public TenantPluginResponse disablePlugin(
      String tenantId, String tenantPluginId, PluginDisableRequest request) {
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);
    String disabledBy = blankToDefault(request == null ? null : request.disabledBy(), "system");

    boolean updated = repository.disablePlugin(tenantId, tenantPluginId, disabledBy);
    if (!updated) {
      throw new AppException("PLUGIN_DISABLE_FAILED", "Plugin was not disabled");
    }

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            tenantId,
            tenantPlugin.pluginId(),
            tenantPlugin.id(),
            "plugin_disabled",
            "Plugin disabled for tenant",
            disabledBy,
            "{}"));

    return toTenantPluginResponse(loadTenantPlugin(tenantId, tenantPluginId));
  }

  public TenantFrontendManifestResponse frontendManifest(String tenantId) {
    List<TenantPluginRecord> enabled =
        repository.listTenantPlugins(tenantId).stream()
            .filter(item -> "enabled".equals(item.status()))
            .toList();

    List<String> keys = enabled.stream().map(TenantPluginRecord::pluginKey).toList();
    List<Object> contributions = new ArrayList<>();

    for (TenantPluginRecord plugin : enabled) {
      Map<String, Object> manifest = json.readMap(plugin.manifestJson());
      Object frontend = manifest.get("frontend");
      if (!(frontend instanceof Map<?, ?> frontendMap)) {
        continue;
      }

      Object rawContributions = frontendMap.get("contributions");
      if (rawContributions instanceof List<?> list) {
        contributions.addAll(list);
      }
    }

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            tenantId,
            null,
            null,
            "manifest_requested",
            "Tenant frontend plugin manifest requested",
            "system",
            json.write(
                Map.of(
                    "pluginCount", enabled.size(),
                    "contributionCount", contributions.size()))));

    return new TenantFrontendManifestResponse(tenantId, keys, contributions);
  }

  public List<TenantPluginToolPolicyResponse> listToolPolicies(String tenantId) {
    return repository.listTenantToolPolicies(tenantId).stream()
        .map(this::toToolPolicyResponse)
        .toList();
  }

  @Transactional
  public TenantPluginToolPolicyResponse allowTool(
      String tenantId, String tenantPluginId, String toolKey, String actor) {
    validator.requireAllowedToolKey(toolKey);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);
    ensureTenantPluginEnabled(tenantPlugin);

    repository.upsertToolPolicy(
        new ToolPolicyCommand(
            newId("tptp"),
            tenantId,
            tenantPlugin.id(),
            tenantPlugin.pluginId(),
            toolKey,
            "allowed",
            riskLevelForTool(tenantPlugin, toolKey),
            blankToDefault(actor, "system")));

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            tenantId,
            tenantPlugin.pluginId(),
            tenantPlugin.id(),
            "tool_allowed",
            "Plugin tool allowed",
            blankToDefault(actor, "system"),
            json.write(Map.of("toolKey", toolKey))));

    return repository
        .findAllowedToolPolicy(tenantId, toolKey)
        .map(this::toToolPolicyResponse)
        .orElseThrow(
            () -> new AppException("PLUGIN_TOOL_POLICY_NOT_FOUND", "Tool policy not found"));
  }

  @Transactional
  public TenantPluginToolPolicyResponse denyTool(
      String tenantId, String tenantPluginId, String toolKey, String actor) {
    validator.requireAllowedToolKey(toolKey);
    TenantPluginRecord tenantPlugin = loadTenantPlugin(tenantId, tenantPluginId);

    repository.upsertToolPolicy(
        new ToolPolicyCommand(
            newId("tptp"),
            tenantId,
            tenantPlugin.id(),
            tenantPlugin.pluginId(),
            toolKey,
            "denied",
            riskLevelForTool(tenantPlugin, toolKey),
            blankToDefault(actor, "system")));

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            tenantId,
            tenantPlugin.pluginId(),
            tenantPlugin.id(),
            "tool_denied",
            "Plugin tool denied",
            blankToDefault(actor, "system"),
            json.write(Map.of("toolKey", toolKey))));

    return repository.listTenantToolPolicies(tenantId).stream()
        .filter(
            item -> item.tenantPluginId().equals(tenantPluginId) && item.toolKey().equals(toolKey))
        .findFirst()
        .map(this::toToolPolicyResponse)
        .orElseThrow(
            () -> new AppException("PLUGIN_TOOL_POLICY_NOT_FOUND", "Tool policy not found"));
  }

  public AgentToolAuthorizeResponse authorizeTool(AgentToolAuthorizeRequest request) {
    if (request == null || request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("PLUGIN_TOOL_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.toolKey() == null || request.toolKey().isBlank()) {
      throw new AppException("PLUGIN_TOOL_KEY_REQUIRED", "Tool key is required");
    }

    validator.requireAllowedToolKey(request.toolKey());

    boolean allowed =
        repository.findAllowedToolPolicy(request.tenantId(), request.toolKey()).isPresent();

    if (allowed) {
      repository.createEvent(
          new PluginEventCommand(
              newId("ple"),
              request.tenantId(),
              null,
              null,
              "tool_authorized",
              "Agent tool authorized",
              "agent",
              json.write(Map.of("toolKey", request.toolKey()))));
      return new AgentToolAuthorizeResponse(
          true, request.toolKey(), "allowed by tenant plugin policy");
    }

    repository.createEvent(
        new PluginEventCommand(
            newId("ple"),
            request.tenantId(),
            null,
            null,
            "tool_denied_by_policy",
            "Agent tool denied by plugin policy",
            "agent",
            json.write(Map.of("toolKey", request.toolKey()))));

    return new AgentToolAuthorizeResponse(
        false, request.toolKey(), "not allowed by tenant plugin policy");
  }

  private void allowDefaultTools(
      String tenantId,
      TenantPluginRecord tenantPlugin,
      PluginDescriptorRecord plugin,
      String actor) {
    Map<String, Object> manifest = json.readMap(plugin.manifestJson());
    Object rawTools = manifest.get("agentTools");
    if (!(rawTools instanceof List<?> tools)) {
      return;
    }

    for (Object item : tools) {
      if (!(item instanceof Map<?, ?> tool)) {
        continue;
      }

      String toolKey = String.valueOf(tool.get("toolKey"));
      validator.requireAllowedToolKey(toolKey);

      repository.upsertToolPolicy(
          new ToolPolicyCommand(
              newId("tptp"),
              tenantId,
              tenantPlugin.id(),
              plugin.id(),
              toolKey,
              "allowed",
              riskLevelFromTool(tool),
              actor));
    }
  }

  private String riskLevelFromTool(Map<?, ?> tool) {
    Object risk = tool.get("riskLevel");
    String riskStr = risk == null ? "low" : String.valueOf(risk);
    return List.of("low", "medium", "high", "critical").contains(riskStr) ? riskStr : "low";
  }

  private String riskLevelForTool(TenantPluginRecord tenantPlugin, String toolKey) {
    Map<String, Object> manifest = json.readMap(tenantPlugin.manifestJson());
    Object rawTools = manifest.get("agentTools");
    if (!(rawTools instanceof List<?> tools)) {
      return "low";
    }

    for (Object item : tools) {
      if (item instanceof Map<?, ?> tool && toolKey.equals(String.valueOf(tool.get("toolKey")))) {
        return riskLevelFromTool(tool);
      }
    }

    return "low";
  }

  private PluginDescriptorRecord loadPlugin(String pluginId) {
    return repository
        .findPlugin(pluginId)
        .orElseThrow(() -> new AppException("PLUGIN_NOT_FOUND", "Plugin not found"));
  }

  private TenantPluginRecord loadTenantPlugin(String tenantId, String tenantPluginId) {
    return repository
        .findTenantPlugin(tenantId, tenantPluginId)
        .orElseThrow(() -> new AppException("TENANT_PLUGIN_NOT_FOUND", "Tenant plugin not found"));
  }

  private void ensurePluginActive(PluginDescriptorRecord plugin) {
    if (!"active".equals(plugin.status())) {
      throw new AppException("PLUGIN_NOT_ACTIVE", "Plugin is not active");
    }
  }

  private void ensureTenantPluginEnabled(TenantPluginRecord plugin) {
    if (!"enabled".equals(plugin.status())) {
      throw new AppException("TENANT_PLUGIN_DISABLED", "Tenant plugin is disabled");
    }
  }

  private PluginDescriptorResponse toPluginResponse(PluginDescriptorRecord record) {
    return new PluginDescriptorResponse(
        record.id(),
        record.pluginKey(),
        record.name(),
        record.version(),
        record.description(),
        record.provider(),
        record.status(),
        record.manifestJson(),
        record.capabilitiesJson(),
        record.createdAt(),
        record.updatedAt());
  }

  private TenantPluginResponse toTenantPluginResponse(TenantPluginRecord record) {
    return new TenantPluginResponse(
        record.id(),
        record.pluginId(),
        record.pluginKey(),
        record.name(),
        record.version(),
        record.status(),
        record.configJson(),
        record.enabledAt(),
        record.disabledAt());
  }

  private TenantPluginToolPolicyResponse toToolPolicyResponse(TenantPluginToolPolicyRecord record) {
    return new TenantPluginToolPolicyResponse(
        record.id(),
        record.tenantPluginId(),
        record.pluginId(),
        record.pluginKey(),
        record.toolKey(),
        record.status(),
        record.riskLevel(),
        record.createdAt(),
        record.updatedAt());
  }

  private String normalizeConfigJson(String value) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    json.readMap(value);
    return value;
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
