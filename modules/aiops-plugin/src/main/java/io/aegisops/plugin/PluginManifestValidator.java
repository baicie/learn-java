package io.aegisops.plugin;

import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PluginManifestValidator {
  public static final Set<String> ALLOWED_EXTENSION_POINTS =
      Set.of(
          "incident.detail.sidebar",
          "incident.detail.action",
          "dashboard.card",
          "settings.page",
          "agent.tool");

  public static final Set<String> ALLOWED_TOOL_KEYS =
      Set.of(
          "evidence.fetch",
          "knowledge.search_cases",
          "checkpoint.create",
          "checkpoint.get",
          "memory.search",
          "memory.create");

  private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9_.-]{1,128}$");

  private final PluginJson json;

  public PluginManifestValidator(PluginJson json) {
    this.json = json;
  }

  public void validateManifest(String manifestJson) {
    Map<String, Object> manifest = json.readMap(manifestJson);

    rejectRemoteCode(manifest);

    Object frontend = manifest.get("frontend");
    if (frontend instanceof Map<?, ?> frontendMap) {
      validateFrontend(frontendMap);
    }

    Object tools = manifest.get("agentTools");
    if (tools instanceof List<?> toolList) {
      validateTools(toolList);
    }
  }

  public void requireAllowedToolKey(String toolKey) {
    if (toolKey == null || !ALLOWED_TOOL_KEYS.contains(toolKey)) {
      throw new AppException("PLUGIN_TOOL_NOT_ALLOWED", "Plugin tool key is not allowed");
    }
  }

  private void validateFrontend(Map<?, ?> frontendMap) {
    Object contributions = frontendMap.get("contributions");
    if (contributions == null) {
      return;
    }

    if (!(contributions instanceof List<?> list)) {
      throw new AppException("PLUGIN_MANIFEST_INVALID", "frontend.contributions must be array");
    }

    for (Object item : list) {
      if (!(item instanceof Map<?, ?> contribution)) {
        throw new AppException("PLUGIN_MANIFEST_INVALID", "frontend contribution must be object");
      }

      String extensionPoint = stringValue(contribution.get("extensionPoint"));
      String contributionId = stringValue(contribution.get("contributionId"));
      String componentKey = stringValue(contribution.get("componentKey"));

      if (!ALLOWED_EXTENSION_POINTS.contains(extensionPoint)) {
        throw new AppException("PLUGIN_EXTENSION_POINT_INVALID", "Invalid extension point");
      }

      if (!SAFE_KEY.matcher(contributionId).matches()) {
        throw new AppException("PLUGIN_CONTRIBUTION_ID_INVALID", "Invalid contribution id");
      }

      if (!SAFE_KEY.matcher(componentKey).matches()) {
        throw new AppException("PLUGIN_COMPONENT_KEY_INVALID", "Invalid component key");
      }

      if (contribution.containsKey("remoteUrl")
          || contribution.containsKey("script")
          || contribution.containsKey("html")) {
        throw new AppException("PLUGIN_REMOTE_CODE_FORBIDDEN", "Remote plugin code is forbidden");
      }
    }
  }

  private void validateTools(List<?> toolList) {
    for (Object item : toolList) {
      if (!(item instanceof Map<?, ?> tool)) {
        throw new AppException("PLUGIN_MANIFEST_INVALID", "agent tool must be object");
      }

      String toolKey = stringValue(tool.get("toolKey"));
      requireAllowedToolKey(toolKey);
    }
  }

  private void rejectRemoteCode(Map<String, Object> manifest) {
    if (manifest.containsKey("remoteEntry")
        || manifest.containsKey("scriptUrl")
        || manifest.containsKey("iframeUrl")) {
      throw new AppException("PLUGIN_REMOTE_CODE_FORBIDDEN", "Remote plugin code is forbidden");
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
