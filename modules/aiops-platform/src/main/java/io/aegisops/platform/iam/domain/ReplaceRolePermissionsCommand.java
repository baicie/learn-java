package io.aegisops.platform.iam.domain;

import java.util.Set;

public record ReplaceRolePermissionsCommand(
    Set<String> permissionCodes, Confirmation confirmation) {

  public record Confirmation(String reason, boolean dangerousAcknowledged) {

    public void requireConfirmed(String roleCode) {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException(
            "dangerous permission change requires a non-empty reason");
      }
      if (!dangerousAcknowledged) {
        throw new IllegalArgumentException(
            "dangerous permission change for role " + roleCode + " must be acknowledged");
      }
    }
  }
}