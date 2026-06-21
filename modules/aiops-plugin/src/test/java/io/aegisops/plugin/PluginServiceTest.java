package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDisableRequest;
import io.aegisops.plugin.dto.PluginEnableRequest;
import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginServiceTest {
  @Test
  void listExtensionPointsReturnsFixedPoints() {
    PluginService service =
        new PluginService(
            new FakePluginRepository(),
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(new PluginJson(new ObjectMapper())),
            new PluginJson(new ObjectMapper()));

    List<PluginExtensionPointResponse> points = service.listExtensionPoints();
    assertEquals(5, points.size());
    assertTrue(points.stream().anyMatch(p -> p.extensionPoint().equals("agent.tool")));
  }

  @Test
  void enablePluginCreatesTenantPlugin() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            """
            {
              "agentTools": [
                {"toolKey": "knowledge.search_cases", "riskLevel": "low"},
                {"toolKey": "memory.search", "riskLevel": "low"}
              ]
            }
            """,
            "{}",
            "system"));

    var enabled = service.enablePlugin("tenant_1", "plg_1", new PluginEnableRequest("alice", "{}"));
    assertEquals("enabled", enabled.status());
  }

  @Test
  void enablePluginRejectsInactivePlugin() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_disabled",
            "builtin.disabled",
            "Disabled Plugin",
            "0.1.0",
            "desc",
            "builtin",
            "disabled",
            "{}",
            "{}",
            "system"));

    assertThrows(AppException.class, () -> service.enablePlugin("tenant_1", "plg_disabled", null));
  }

  @Test
  void disablePluginUpdatesStatus() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            "{}",
            "{}",
            "system"));

    String tenantPluginId = repository.enablePlugin("tenant_1", "plg_1", "{}", "alice");

    var result = service.disablePlugin("tenant_1", tenantPluginId, new PluginDisableRequest("bob"));
    assertEquals("disabled", result.status());
  }

  @Test
  void disablePluginThrowsWhenNotFound() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    assertThrows(
        AppException.class,
        () -> service.disablePlugin("tenant_1", "nonexistent", new PluginDisableRequest("bob")));
  }

  @Test
  void listPluginsReturnsAll() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1", "builtin.a", "A", "0.1.0", "desc", "builtin", "active", "{}", "{}", "system"));
    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_2", "builtin.b", "B", "0.1.0", "desc", "builtin", "active", "{}", "{}", "system"));

    var plugins = service.listPlugins();
    assertEquals(2, plugins.size());
  }

  @Test
  void getPluginReturnsPlugin() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            "{}",
            "{}",
            "system"));

    var plugin = service.getPlugin("plg_1");
    assertEquals("builtin.incident-copilot", plugin.pluginKey());
    assertEquals("Incident Copilot", plugin.name());
  }

  @Test
  void getPluginThrowsWhenNotFound() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    assertThrows(AppException.class, () -> service.getPlugin("nonexistent"));
  }

  @Test
  void listTenantPluginsReturnsEnabled() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1", "builtin.a", "A", "0.1.0", "desc", "builtin", "active", "{}", "{}", "system"));
    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_2", "builtin.b", "B", "0.1.0", "desc", "builtin", "active", "{}", "{}", "system"));

    repository.enablePlugin("tenant_1", "plg_1", "{}", "alice");
    repository.enablePlugin("tenant_1", "plg_2", "{}", "alice");

    var tenantPlugins = service.listTenantPlugins("tenant_1");
    assertEquals(2, tenantPlugins.size());
  }

  @Test
  void allowToolRejectsToolNotDeclaredByPluginManifest() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            """
            {
              "frontend": {
                "contributions": [
                  {
                    "extensionPoint": "incident.detail.sidebar",
                    "contributionId": "panel",
                    "componentKey": "builtin.panel"
                  }
                ]
              },
              "agentTools": [
                {"toolKey": "knowledge.search_cases", "riskLevel": "low"}
              ]
            }
            """,
            "{}",
            "system"));

    var enabled = service.enablePlugin("tenant_1", "plg_1", new PluginEnableRequest("alice", "{}"));

    assertThrows(
        AppException.class,
        () -> service.allowTool("tenant_1", enabled.id(), "memory.create", "alice"));
  }

  @Test
  void authorizeToolDeniesPolicyWhenToolIsNotDeclaredByPluginManifest() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            "plg_1",
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "desc",
            "builtin",
            "active",
            """
            {
              "agentTools": [
                {"toolKey": "knowledge.search_cases", "riskLevel": "low"}
              ]
            }
            """,
            "{}",
            "system"));

    String tenantPluginId = repository.enablePlugin("tenant_1", "plg_1", "{}", "alice");

    repository.upsertToolPolicy(
        new ToolPolicyCommand(
            "tptp_invalid",
            "tenant_1",
            tenantPluginId,
            "plg_1",
            "memory.create",
            "allowed",
            "low",
            "test"));

    var response =
        service.authorizeTool(
            "tenant_1", new AgentToolAuthorizeRequest("tenant_1", "memory.create"));

    assertFalse(response.allowed());
    assertEquals("memory.create", response.toolKey());
  }

  @Test
  void authorizeToolRejectsTenantMismatch() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    assertThrows(
        AppException.class,
        () ->
            service.authorizeTool(
                "tenant_header", new AgentToolAuthorizeRequest("tenant_body", "memory.search")));
  }
}
