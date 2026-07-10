package io.aegisops.security;

import java.util.Set;

public final class BuiltInRoleCodes {
  private BuiltInRoleCodes() {}

  public static final String SYSTEM_ADMIN = "system_admin";
  public static final String RECORD_ADMIN = "record_admin";
  public static final String NORMAL_USER = "normal_user";
  public static final String READONLY_USER = "readonly_user";

  public static final Set<String> ALL =
      Set.of(
          SYSTEM_ADMIN,
          RECORD_ADMIN,
          NORMAL_USER,
          READONLY_USER);
}
