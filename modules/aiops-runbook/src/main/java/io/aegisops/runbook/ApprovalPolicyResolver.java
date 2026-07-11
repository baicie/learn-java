package io.aegisops.runbook;

import io.aegisops.runbook.dto.ApprovalPolicyRecord;

public class ApprovalPolicyResolver {
  private final RunbookRepository repository;

  public ApprovalPolicyResolver(RunbookRepository repository) {
    this.repository = repository;
  }

  public ApprovalPolicyRecord resolve(String tenantId, String riskLevel) {
    String normalizedRisk = normalizeRisk(riskLevel);

    return repository
        .findApprovalPolicy(tenantId, normalizedRisk)
        .filter(policy -> policy.enabled())
        .or(
            () ->
                repository
                    .findGlobalApprovalPolicy(normalizedRisk)
                    .filter(policy -> policy.enabled()))
        .orElseGet(() -> fallbackPolicy(normalizedRisk));
  }

  private ApprovalPolicyRecord fallbackPolicy(String riskLevel) {
    return switch (riskLevel) {
      case "low" ->
          new ApprovalPolicyRecord("fallback_low", null, "low", 0, false, true, null, null);
      case "high" ->
          new ApprovalPolicyRecord("fallback_high", null, "high", 1, true, true, null, null);
      case "critical" ->
          new ApprovalPolicyRecord(
              "fallback_critical", null, "critical", 2, true, true, null, null);
      default ->
          new ApprovalPolicyRecord("fallback_medium", null, "medium", 1, false, true, null, null);
    };
  }

  private String normalizeRisk(String riskLevel) {
    if (riskLevel == null || riskLevel.isBlank()) {
      return "medium";
    }

    return switch (riskLevel) {
      case "low", "medium", "high", "critical" -> riskLevel;
      default -> "medium";
    };
  }
}
