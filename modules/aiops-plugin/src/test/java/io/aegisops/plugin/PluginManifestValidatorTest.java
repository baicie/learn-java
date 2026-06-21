package io.aegisops.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

class PluginManifestValidatorTest {
  private final PluginManifestValidator validator =
      new PluginManifestValidator(new PluginJson(new ObjectMapper()));

  @Test
  void allowSafeManifest() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "incident.detail.sidebar",
                "contributionId": "incident-copilot-panel",
                "title": "Incident Copilot",
                "componentKey": "builtin.incidentCopilotPanel",
                "order": 100,
                "props": {}
              }
            ]
          },
          "agentTools": [
            {
              "toolKey": "knowledge.search_cases",
              "riskLevel": "low"
            }
          ]
        }
        """;

    assertDoesNotThrow(() -> validator.validateManifest(manifest));
  }

  @Test
  void rejectRemoteEntry() {
    String manifest =
        """
        {"remoteEntry": "https://evil.example/plugin.js"}
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectScriptUrl() {
    String manifest =
        """
        {"scriptUrl": "https://evil.example/plugin.js"}
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectIframeUrl() {
    String manifest =
        """
        {"iframeUrl": "https://evil.example/plugin.html"}
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectContributionWithRemoteUrl() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "incident.detail.sidebar",
                "contributionId": "panel",
                "componentKey": "builtin.panel",
                "remoteUrl": "https://evil.example/panel.js"
              }
            ]
          }
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectContributionWithScript() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "incident.detail.sidebar",
                "contributionId": "panel",
                "componentKey": "builtin.panel",
                "script": "alert(1)"
              }
            ]
          }
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectContributionWithHtml() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "incident.detail.sidebar",
                "contributionId": "panel",
                "componentKey": "builtin.panel",
                "html": "<script>evil</script>"
              }
            ]
          }
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectInvalidExtensionPoint() {
    String manifest =
        """
        {
          "frontend": {
            "contributions": [
              {
                "extensionPoint": "unknown.point",
                "contributionId": "bad",
                "componentKey": "builtin.bad"
              }
            ]
          }
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectUnknownToolKey() {
    String manifest =
        """
        {
          "agentTools": [
            {"toolKey": "ssh.exec", "riskLevel": "critical"}
          ]
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void rejectNullToolKey() {
    String manifest =
        """
        {
          "agentTools": [
            {"toolKey": null, "riskLevel": "low"}
          ]
        }
        """;

    assertThrows(AppException.class, () -> validator.validateManifest(manifest));
  }

  @Test
  void requireAllowedToolKeyAcceptsValid() {
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("knowledge.search_cases"));
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("evidence.fetch"));
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("checkpoint.create"));
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("checkpoint.get"));
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("memory.search"));
    assertDoesNotThrow(() -> validator.requireAllowedToolKey("memory.create"));
  }

  @Test
  void requireAllowedToolKeyRejectsInvalid() {
    assertThrows(AppException.class, () -> validator.requireAllowedToolKey("ssh.exec"));
    assertThrows(AppException.class, () -> validator.requireAllowedToolKey("webhook.send"));
    assertThrows(AppException.class, () -> validator.requireAllowedToolKey(null));
  }
}
