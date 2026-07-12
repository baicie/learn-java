package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.domain.CreateRoleData;
import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import io.aegisops.platform.iam.domain.ReplaceRoleDataScopesCommand;
import io.aegisops.platform.iam.domain.ReplaceRolePermissionsCommand;
import io.aegisops.platform.iam.domain.UpdateRoleData;
import io.aegisops.platform.iam.service.PlatformRoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/roles")
public class PlatformRoleController {

  private final PlatformRoleService service;

  public PlatformRoleController(PlatformRoleService service) {
    this.service = service;
  }

  @GetMapping
  public List<PlatformRole> list(
      @RequestParam(value = "includeSystem", defaultValue = "true") boolean includeSystem) {
    return service.list(includeSystem);
  }

  @GetMapping("/{code}")
  public PlatformRoleDetail detail(@PathVariable String code) {
    return service.detail(code);
  }

  @PostMapping
  public PlatformRole create(@Valid @RequestBody CreateRoleData body) {
    return service.create(body, currentActor());
  }

  @PutMapping("/{code}")
  public PlatformRoleDetail update(
      @PathVariable String code, @Valid @RequestBody UpdateRoleData body) {
    return service.update(code, body, currentActor());
  }

  @PostMapping("/{code}/permissions")
  public Map<String, Object> replacePermissions(
      @PathVariable String code, @Valid @RequestBody ReplaceRolePermissionsCommand body) {
    service.replacePermissions(code, body, currentActor());
    return Map.of("status", "ok", "code", code);
  }

  @PostMapping("/{code}/data-scopes")
  public Map<String, Object> replaceDataScopes(
      @PathVariable String code, @Valid @RequestBody ReplaceRoleDataScopesCommand body) {
    service.replaceDataScopes(code, body, currentActor());
    return Map.of("status", "ok", "code", code);
  }

  @DeleteMapping("/{code}")
  public Map<String, Object> delete(@PathVariable String code) {
    service.delete(code, currentActor());
    return Map.of("status", "ok", "code", code);
  }

  private static String currentActor() {
    try {
      Object principal =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication()
              .getPrincipal();
      if (principal instanceof io.aegisops.security.UserPrincipal up) {
        return up.id();
      }
    } catch (Throwable ignored) {
      // ignore - principal may not be wired for tests
    }
    return "system";
  }
}