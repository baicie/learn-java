package io.aegisops.workrecord.application.service;

import io.aegisops.security.DataScope;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.WorkRecord;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordPermissionService {
  private static final String RESOURCE_CODE = "work-record";

  public boolean canReadAll(UserPrincipal principal) {
    return principal != null
        && principal.hasPermission(PermissionCodes.WORK_RECORD_READ_ALL)
        && principal.dataScope(RESOURCE_CODE) == DataScope.ALL;
  }

  public boolean canReadSelf(UserPrincipal principal) {
    return principal != null
        && principal.hasAnyPermission(
            PermissionCodes.WORK_RECORD_READ_SELF, PermissionCodes.WORK_RECORD_READ_ALL);
  }

  public void requireCreate(UserPrincipal principal) {
    requirePermission(
        principal, PermissionCodes.WORK_RECORD_WRITE, "not allowed to create work records");
  }

  public void requireRead(UserPrincipal principal, WorkRecord record) {
    if (canReadAll(principal)) {
      return;
    }

    if (!canReadSelf(principal)) {
      throw new AccessDeniedException("not allowed to read work records");
    }

    requireSelfRecord(principal, record, "not allowed to read this work record");
  }

  public void requireEdit(UserPrincipal principal, WorkRecord record) {
    requirePermission(
        principal, PermissionCodes.WORK_RECORD_WRITE, "not allowed to edit work records");

    if (hasAllDataScope(principal)) {
      return;
    }

    requireSelfRecord(principal, record, "not allowed to edit this work record");
  }

  public void requireDelete(UserPrincipal principal, WorkRecord record) {
    requirePermission(
        principal, PermissionCodes.WORK_RECORD_DELETE, "not allowed to delete work records");

    if (hasAllDataScope(principal)) {
      return;
    }

    requireSelfRecord(principal, record, "not allowed to delete this work record");
  }

  public void requireExport(UserPrincipal principal) {
    requirePermission(
        principal, PermissionCodes.WORK_RECORD_EXPORT, "not allowed to export work records");
  }

  public boolean hasAllDataScope(UserPrincipal principal) {
    return principal != null && principal.dataScope(RESOURCE_CODE) == DataScope.ALL;
  }

  private void requireSelfRecord(UserPrincipal principal, WorkRecord record, String message) {
    if (principal == null || record == null) {
      throw new AccessDeniedException(message);
    }

    String userId = principal.id();

    boolean creator = userId.equals(record.creatorId());

    boolean owner = record.ownerId() != null && userId.equals(record.ownerId());

    if (!creator && !owner) {
      throw new AccessDeniedException(message);
    }
  }

  private void requirePermission(UserPrincipal principal, String permission, String message) {
    if (principal == null || !principal.hasPermission(permission)) {
      throw new AccessDeniedException(message);
    }
  }
}
