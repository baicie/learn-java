package io.aegisops.security;

import java.util.Set;

public final class PermissionCodes {
  private PermissionCodes() {}

  public static final String PLATFORM_DICT_READ =
      "platform:dict:read";
  public static final String PLATFORM_DICT_WRITE =
      "platform:dict:write";

  public static final String PLATFORM_CALENDAR_READ =
      "platform:calendar:read";
  public static final String PLATFORM_CALENDAR_WRITE =
      "platform:calendar:write";
  public static final String PLATFORM_CALENDAR_IMPORT =
      "platform:calendar:import";

  public static final String WORK_RECORD_TEMPLATE_READ =
      "work-record:template:read";
  public static final String WORK_RECORD_TEMPLATE_WRITE =
      "work-record:template:write";

  public static final String WORK_RECORD_READ_SELF =
      "work-record:read:self";
  public static final String WORK_RECORD_READ_ALL =
      "work-record:read:all";
  public static final String WORK_RECORD_WRITE =
      "work-record:write";
  public static final String WORK_RECORD_DELETE =
      "work-record:delete";
  public static final String WORK_RECORD_EXPORT =
      "work-record:export";

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

  public static void requireKnown(String permissionCode) {
    if (!PHASE_13_PERMISSIONS.contains(permissionCode)) {
      throw new IllegalArgumentException(
          "unknown permission code: " + permissionCode);
    }
  }
}
