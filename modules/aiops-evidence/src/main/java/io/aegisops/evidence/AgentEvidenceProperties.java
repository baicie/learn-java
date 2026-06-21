package io.aegisops.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent.evidence")
public record AgentEvidenceProperties(
    String internalToken,
    Integer defaultLookbackMinutes,
    Integer maxLogPatterns,
    Integer maxChanges) {
  public String normalizedInternalToken() {
    return internalToken == null || internalToken.isBlank()
        ? "dev-internal-token"
        : internalToken.trim();
  }

  public int normalizedDefaultLookbackMinutes() {
    return defaultLookbackMinutes == null || defaultLookbackMinutes <= 0
        ? 60
        : defaultLookbackMinutes;
  }

  public int normalizedMaxLogPatterns() {
    return maxLogPatterns == null || maxLogPatterns <= 0 ? 20 : maxLogPatterns;
  }

  public int normalizedMaxChanges() {
    return maxChanges == null || maxChanges <= 0 ? 20 : maxChanges;
  }
}
