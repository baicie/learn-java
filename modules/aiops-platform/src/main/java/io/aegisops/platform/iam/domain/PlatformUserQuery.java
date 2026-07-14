package io.aegisops.platform.iam.domain;

import java.util.Set;

public record PlatformUserQuery(
    String keyword, PlatformUserStatus status, Set<String> roleCodes, int page, int pageSize) {

  public PlatformUserQuery normalized() {
    int safePage = page <= 0 ? 1 : page;
    int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
    Set<String> safeRoles = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
    PlatformUserStatus safeStatus = status == null ? null : status;
    String safeKeyword = keyword == null ? null : keyword.trim();
    return new PlatformUserQuery(safeKeyword, safeStatus, safeRoles, safePage, safePageSize);
  }
}
