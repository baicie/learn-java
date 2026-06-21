package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BuiltinPluginSeeder implements ApplicationRunner {
  private final PluginRepository repository;
  private final PluginJson json;
  private final PluginManifestValidator validator;

  public BuiltinPluginSeeder(
      PluginRepository repository, PluginJson json, PluginManifestValidator validator) {
    this.repository = repository;
    this.json = json;
    this.validator = validator;
  }

  @Override
  public void run(ApplicationArguments args) {
    registerIncidentCopilot();
  }

  private void registerIncidentCopilot() {
    String manifest =
        json.write(
            Map.of(
                "frontend",
                Map.of(
                    "contributions",
                    List.of(
                        Map.of(
                            "extensionPoint",
                            "incident.detail.sidebar",
                            "contributionId",
                            "incident-copilot-panel",
                            "title",
                            "Incident Copilot",
                            "componentKey",
                            "builtin.incidentCopilotPanel",
                            "order",
                            100,
                            "props",
                            Map.of()))),
                "agentTools",
                List.of(
                    Map.of(
                        "toolKey",
                        "knowledge.search_cases",
                        "riskLevel",
                        "low",
                        "description",
                        "Search similar incident cases"),
                    Map.of(
                        "toolKey",
                        "memory.search",
                        "riskLevel",
                        "low",
                        "description",
                        "Search tenant agent memory"))));

    validator.validateManifest(manifest);

    repository.upsertDescriptor(
        new PluginDescriptorCreateCommand(
            stableId("builtin.incident-copilot", "0.1.0"),
            "builtin.incident-copilot",
            "Incident Copilot",
            "0.1.0",
            "Built-in incident assistant plugin",
            "builtin",
            "active",
            manifest,
            json.write(
                Map.of(
                    "extensionPoints",
                    List.of("incident.detail.sidebar", "agent.tool"),
                    "agentTools",
                    List.of("knowledge.search_cases", "memory.search"))),
            "system"));
  }

  private String stableId(String pluginKey, String version) {
    return "plg_"
        + UUID.nameUUIDFromBytes((pluginKey + ":" + version).getBytes())
            .toString()
            .replace("-", "");
  }
}
