package io.aegisops.security;

import io.aegisops.common.security.AuthenticatedActor;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails, AuthenticatedActor {
  private final String id;
  private final String tenantId;
  private final String username;
  private final String displayName;
  private final Set<String> roles;
  private final Set<String> permissions;
  private final Map<String, DataScope> dataScopes;

  public UserPrincipal(
      String id,
      String tenantId,
      String username,
      String displayName,
      Set<String> roles,
      Set<String> permissions,
      Map<String, DataScope> dataScopes) {
    this.id = requireText(id, "id");
    this.tenantId = requireText(tenantId, "tenantId");
    this.username = requireText(username, "username");
    this.displayName = displayName == null || displayName.isBlank() ? username : displayName;

    this.roles = roles == null ? Set.of() : Set.copyOf(roles);

    this.permissions = permissions == null ? Set.of() : Set.copyOf(permissions);

    this.dataScopes = dataScopes == null ? Map.of() : Map.copyOf(dataScopes);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String tenantId() {
    return tenantId;
  }

  public String displayName() {
    return displayName;
  }

  public Set<String> roles() {
    return roles;
  }

  public Set<String> permissions() {
    return permissions;
  }

  public Map<String, DataScope> dataScopes() {
    return dataScopes;
  }

  public boolean hasPermission(String permissionCode) {
    return permissions.contains(permissionCode);
  }

  public boolean hasAnyPermission(String... permissionCodes) {
    if (permissionCodes == null) {
      return false;
    }

    for (String code : permissionCodes) {
      if (permissions.contains(code)) {
        return true;
      }
    }

    return false;
  }

  public DataScope dataScope(String resourceCode) {
    return dataScopes.getOrDefault(resourceCode, DataScope.SELF);
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();

    for (String role : roles) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.replace('-', '_').toUpperCase()));
    }

    for (String permission : permissions) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }

    return Set.copyOf(authorities);
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }

    return value;
  }
}
