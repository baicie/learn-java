package io.aegisops.security;

import java.util.HashSet;
import java.util.Set;

public final class PermissionCodes {
  private PermissionCodes() {}

  // Legacy AIOps permissions
  public static final String DATASOURCE_READ = "datasource:read";
  public static final String DATASOURCE_WRITE = "datasource:write";
  public static final String ASSET_READ = "asset:read";
  public static final String ALERT_READ = "alert:read";
  public static final String ALERT_WRITE = "alert:write";
  public static final String INCIDENT_READ = "incident:read";
  public static final String INCIDENT_WRITE = "incident:write";
  public static final String INCIDENT_DIAGNOSE = "incident:diagnose";
  public static final String RUNBOOK_READ = "runbook:read";
  public static final String RUNBOOK_WRITE = "runbook:write";
  public static final String AUTOMATION_READ = "automation:read";
  public static final String AUTOMATION_APPROVE = "automation:approve";
  public static final String AUTOMATION_EXECUTE = "automation:execute";
  public static final String AUDIT_READ = "audit:read";
  public static final String ADMIN_MANAGE = "admin:manage";

  // Phase 13 permissions
  public static final String PLATFORM_DICT_READ = "platform:dict:read";
  public static final String PLATFORM_DICT_WRITE = "platform:dict:write";
  public static final String PLATFORM_CALENDAR_READ = "platform:calendar:read";
  public static final String PLATFORM_CALENDAR_WRITE = "platform:calendar:write";
  public static final String PLATFORM_CALENDAR_IMPORT = "platform:calendar:import";

  public static final String WORK_RECORD_TEMPLATE_READ = "work-record:template:read";
  public static final String WORK_RECORD_TEMPLATE_WRITE = "work-record:template:write";
  public static final String WORK_RECORD_READ_SELF = "work-record:read:self";
  public static final String WORK_RECORD_READ_ALL = "work-record:read:all";
  public static final String WORK_RECORD_WRITE = "work-record:write";
  public static final String WORK_RECORD_DELETE = "work-record:delete";
  public static final String WORK_RECORD_EXPORT = "work-record:export";
  public static final String WORK_RECORD_IMPORT = "work-record:import";
  public static final String WORK_RECORD_EXPORT_ASYNC = "work-record:export:async";
  public static final String WORK_RECORD_COMMENT = "work-record:comment";
  public static final String WORK_RECORD_COMMENT_MODERATE = "work-record:comment:moderate";
  public static final String WORK_RECORD_ATTACHMENT = "work-record:attachment";
  public static final String WORK_RECORD_ATTACHMENT_MODERATE = "work-record:attachment:moderate";
  public static final String WORK_RECORD_RELATION = "work-record:relation";

  public static final String PLATFORM_USER_READ = "platform:user:read";
  public static final String PLATFORM_USER_WRITE = "platform:user:write";
  public static final String PLATFORM_USER_STATUS = "platform:user:status";
  public static final String PLATFORM_USER_ASSIGN_ROLE = "platform:user:assign-role";
  public static final String PLATFORM_USER_RESET_PASSWORD = "platform:user:reset-password";
  public static final String PLATFORM_ROLE_READ = "platform:role:read";
  public static final String PLATFORM_ROLE_WRITE = "platform:role:write";

  public static final Set<String> LEGACY_AIOPS_PERMISSIONS =
      Set.of(
          DATASOURCE_READ,
          DATASOURCE_WRITE,
          ASSET_READ,
          ALERT_READ,
          ALERT_WRITE,
          INCIDENT_READ,
          INCIDENT_WRITE,
          INCIDENT_DIAGNOSE,
          RUNBOOK_READ,
          RUNBOOK_WRITE,
          AUTOMATION_READ,
          AUTOMATION_APPROVE,
          AUTOMATION_EXECUTE,
          AUDIT_READ,
          ADMIN_MANAGE);

  public static final Set<String> PHASE_13_PERMISSIONS =
      Set.of(
          PLATFORM_DICT_READ,
          PLATFORM_DICT_WRITE,
          PLATFORM_CALENDAR_READ,
          PLATFORM_CALENDAR_WRITE,
          PLATFORM_CALENDAR_IMPORT,
          WORK_RECORD_TEMPLATE_READ,
          WORK_RECORD_TEMPLATE_WRITE,
          WORK_RECORD_READ_SELF,
          WORK_RECORD_READ_ALL,
          WORK_RECORD_WRITE,
          WORK_RECORD_DELETE,
          WORK_RECORD_EXPORT);

  public static final Set<String> PORTAL_IAM_PERMISSIONS =
      Set.of(
          PLATFORM_USER_READ,
          PLATFORM_USER_WRITE,
          PLATFORM_USER_STATUS,
          PLATFORM_USER_ASSIGN_ROLE,
          PLATFORM_USER_RESET_PASSWORD,
          PLATFORM_ROLE_READ,
          PLATFORM_ROLE_WRITE);

  public static final Set<String> PHASE_20_PERMISSIONS =
      Set.of(
          WORK_RECORD_IMPORT,
          WORK_RECORD_EXPORT_ASYNC,
          WORK_RECORD_COMMENT,
          WORK_RECORD_COMMENT_MODERATE,
          WORK_RECORD_ATTACHMENT,
          WORK_RECORD_ATTACHMENT_MODERATE,
          WORK_RECORD_RELATION);

  public static final Set<String> ALL_PERMISSIONS =
      union(
          union(union(LEGACY_AIOPS_PERMISSIONS, PHASE_13_PERMISSIONS), PORTAL_IAM_PERMISSIONS),
          PHASE_20_PERMISSIONS);

  public static void requireKnown(String permissionCode) {
    if (!ALL_PERMISSIONS.contains(permissionCode)) {
      throw new IllegalArgumentException("unknown permission code: " + permissionCode);
    }
  }

  private static Set<String> union(Set<String> left, Set<String> right) {
    Set<String> result = new HashSet<>(left);
    result.addAll(right);
    return Set.copyOf(result);
  }
}
