package io.aegisops.workrecord.application.service;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.WorkRecord;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordPermissionService {
  public boolean hasAuthority(UserPrincipal user, String authority) {
    if (user == null) {
      return false;
    }
    return user.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
  }

  public boolean canReadAll(UserPrincipal user) {
    return hasAuthority(user, "work-record:read:all");
  }

  public boolean canReadSelf(UserPrincipal user) {
    return hasAuthority(user, "work-record:read:self");
  }

  public boolean canWrite(UserPrincipal user) {
    return hasAuthority(user, "work-record:write");
  }

  public boolean canDelete(UserPrincipal user) {
    return hasAuthority(user, "work-record:delete");
  }

  public boolean canExport(UserPrincipal user) {
    return hasAuthority(user, "work-record:export");
  }

  public void requireRead(UserPrincipal user, WorkRecord record) {
    if (canReadAll(user)) {
      return;
    }
    if (user != null
        && canReadSelf(user)
        && user.id() != null
        && (user.id().equals(record.creatorId()) || user.id().equals(record.ownerId()))) {
      return;
    }
    throw new SecurityException("not allowed to read this work record");
  }

  public void requireWrite(UserPrincipal user, WorkRecord record) {
    if (canReadAll(user)) {
      return;
    }
    if (user != null
        && canWrite(user)
        && user.id() != null
        && (user.id().equals(record.creatorId()) || user.id().equals(record.ownerId()))) {
      return;
    }
    throw new SecurityException("not allowed to update this work record");
  }

  public void requireExport(UserPrincipal user) {
    if (!canExport(user)) {
      throw new SecurityException("not allowed to export work records");
    }
  }
}
