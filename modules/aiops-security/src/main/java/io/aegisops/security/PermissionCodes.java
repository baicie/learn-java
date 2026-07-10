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

  public static final Set<String> ALL_PERMISSIONS =
      union(LEGACY_AIOPS_PERMISSIONS, PHASE_13_PERMISSIONS);

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
