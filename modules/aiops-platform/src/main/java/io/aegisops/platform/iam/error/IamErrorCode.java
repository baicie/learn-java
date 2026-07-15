package io.aegisops.platform.iam.error;

/**
 * Standard error codes surfaced by the portal IAM management plane.
 *
 * <p>Each code maps 1-to-1 to the HTTP status used by the management controllers and is the only
 * way the front-end distinguishes a conflict from a validation failure.
 */
public enum IamErrorCode {
  USER_NOT_FOUND(404, "platform.user.not_found"),
  ROLE_NOT_FOUND(404, "platform.role.not_found"),
  PERMISSION_NOT_FOUND(404, "platform.permission.not_found"),
  PERMISSION_DIRECTORY_INCOMPLETE(422, "platform.permission.directory.incomplete"),
  USERNAME_CONFLICT(409, "platform.user.username_conflict"),
  PERMISSION_REMOVED_FOR_ACTIVE_ROLE(409, "platform.role.permission_removed_for_active_role"),
  ROLE_HAS_ACTIVE_USERS(409, "platform.role.has_active_users"),
  USER_VERSION_CONFLICT(409, "platform.user.version_conflict"),
  SELF_STATUS_CHANGE_FORBIDDEN(409, "platform.user.self_status_change_forbidden"),
  SELF_ROLE_REMOVAL_FORBIDDEN(409, "platform.user.self_role_removal_forbidden"),
  LAST_SYSTEM_ADMIN_REQUIRED(409, "platform.user.last_system_admin_required"),
  ROLE_VERSION_CONFLICT(409, "platform.role.version_conflict"),
  PROTECTED_ROLE_MODIFIED(403, "platform.role.protected"),
  PERMISSION_DENIED(403, "platform.permission.denied"),
  VALIDATION_FAILED(400, "platform.validation.failed");

  private final int status;
  private final String code;

  IamErrorCode(int status, String code) {
    this.status = status;
    this.code = code;
  }

  public int httpStatus() {
    return status;
  }

  public String code() {
    return code;
  }
}
