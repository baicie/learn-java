package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.domain.CreateRoleData;
import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import io.aegisops.platform.iam.domain.ReplaceRoleDataScopesCommand;
import io.aegisops.platform.iam.domain.ReplaceRolePermissionsCommand;
import io.aegisops.platform.iam.domain.UpdateRoleData;
import io.aegisops.platform.iam.service.PlatformRoleService;
import io.aegisops.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  @PreAuthorize("hasAuthority('platform:role:read')")
  public List<PlatformRole> list(
      @RequestParam(value = "includeSystem", defaultValue = "true") boolean includeSystem) {
    return service.list(includeSystem);
  }

  @GetMapping("/{code}")
  @PreAuthorize("hasAuthority('platform:role:read')")
  public PlatformRoleDetail detail(@PathVariable String code) {
    return service.detail(code);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('platform:role:write')")
  public PlatformRole create(
      @Valid @RequestBody CreateRoleData body, @AuthenticationPrincipal UserPrincipal actor) {
    return service.create(body, actor.id());
  }

  @PutMapping("/{code}")
  @PreAuthorize("hasAuthority('platform:role:write')")
  public PlatformRoleDetail update(
      @PathVariable String code,
      @Valid @RequestBody UpdateRoleData body,
      @AuthenticationPrincipal UserPrincipal actor) {
    return service.update(code, body, actor.id());
  }

  @PostMapping("/{code}/permissions")
  @PreAuthorize("hasAuthority('platform:role:write')")
  public PlatformRoleDetail replacePermissions(
      @PathVariable String code,
      @Valid @RequestBody ReplaceRolePermissionsCommand body,
      @AuthenticationPrincipal UserPrincipal actor) {
    service.replacePermissions(code, body, actor.id());
    return service.detail(code);
  }

  @PostMapping("/{code}/data-scopes")
  @PreAuthorize("hasAuthority('platform:role:write')")
  public PlatformRoleDetail replaceDataScopes(
      @PathVariable String code,
      @Valid @RequestBody ReplaceRoleDataScopesCommand body,
      @AuthenticationPrincipal UserPrincipal actor) {
    service.replaceDataScopes(code, body, actor.id());
    return service.detail(code);
  }

  @DeleteMapping("/{code}")
  @PreAuthorize("hasAuthority('platform:role:write')")
  public RoleDeletionResponse delete(
      @PathVariable String code, @AuthenticationPrincipal UserPrincipal actor) {
    service.delete(code, actor.id());
    return new RoleDeletionResponse("ok", code);
  }

  public record RoleDeletionResponse(String status, String code) {}
}
