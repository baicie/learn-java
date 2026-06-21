package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.plugin.dto.AgentToolAuthorizeRequest;
import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginEnableRequest;
import org.junit.jupiter.api.Test;

class PluginServiceAuthorizeTest {
  @Test
  void authorizeToolDeniesWhenNoPolicy() {
    FakePluginRepository repository = new FakePluginRepository();
    PluginJson json = new PluginJson(new ObjectMapper());
    PluginService service =
        new PluginService(
            repository,
            new PluginExtensionPointRegistry(),
            new PluginManifestValidator(json),
            json);

    var auth = service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "memory.search"));
    assertFalse(auth.allowed());
    assertEquals("not allowed by tenant plugin policy", auth.reason());
  }

  @Test
  void enablePluginAllowsDefaultTools() {
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

    service.enablePlugin("tenant_1", "plg_1", new PluginEnableRequest("alice", "{}"));

    var auth1 =
        service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "knowledge.search_cases"));
    assertTrue(auth1.allowed());

    var auth2 = service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "memory.search"));
    assertTrue(auth2.allowed());
  }

  @Test
  void denyToolThenAllowTool() {
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
                {"toolKey": "memory.search", "riskLevel": "low"}
              ]
            }
            """,
            "{}",
            "system"));

    String tenantPluginId = repository.enablePlugin("tenant_1", "plg_1", "{}", "alice");

    service.denyTool("tenant_1", tenantPluginId, "memory.search", "alice");

    var afterDeny =
        service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "memory.search"));
    assertFalse(afterDeny.allowed());

    service.allowTool("tenant_1", tenantPluginId, "memory.search", "alice");

    var afterAllow =
        service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "memory.search"));
    assertTrue(afterAllow.allowed());
  }

  @Test
  void allowToolRequiresTenantPluginEnabled() {
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
    repository.disablePlugin("tenant_1", tenantPluginId, "alice");

    assertThrows(
        AppException.class,
        () -> service.allowTool("tenant_1", tenantPluginId, "memory.search", "alice"));
  }

  @Test
  void authorizeToolRequiresTenantId() {
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
        () -> service.authorizeTool(new AgentToolAuthorizeRequest(null, "memory.search")));

    assertThrows(
        AppException.class,
        () -> service.authorizeTool(new AgentToolAuthorizeRequest("  ", "memory.search")));
  }

  @Test
  void authorizeToolRequiresToolKey() {
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
        () -> service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", null)));

    assertThrows(
        AppException.class,
        () -> service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "  ")));
  }

  @Test
  void authorizeToolRejectsUnknownToolKey() {
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
        () -> service.authorizeTool(new AgentToolAuthorizeRequest("tenant_1", "ssh.exec")));
  }
}
