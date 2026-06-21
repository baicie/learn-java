package io.aegisops.plugin;

import io.aegisops.plugin.dto.PluginExtensionPointResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PluginExtensionPointRegistry {
  public List<PluginExtensionPointResponse> list() {
    return List.of(
        new PluginExtensionPointResponse(
            "incident.detail.sidebar", "Incident detail sidebar panel", true, false),
        new PluginExtensionPointResponse(
            "incident.detail.action", "Incident detail action button", true, false),
        new PluginExtensionPointResponse(
            "dashboard.card", "Dashboard card contribution", true, false),
        new PluginExtensionPointResponse(
            "settings.page", "Settings page contribution", true, false),
        new PluginExtensionPointResponse(
            "agent.tool", "Agent tool allowlist contribution", false, true));
  }
}
