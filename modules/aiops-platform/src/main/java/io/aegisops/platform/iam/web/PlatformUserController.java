package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.domain.ChangeUserStatusCommand;
import io.aegisops.platform.iam.domain.CreatePlatformUserData;
import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserPage;
import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import io.aegisops.platform.iam.domain.ReplaceUserRolesCommand;
import io.aegisops.platform.iam.domain.ResetPlatformUserPasswordCommand;
import io.aegisops.platform.iam.domain.UpdatePlatformUserData;
import io.aegisops.platform.iam.service.PlatformUserService;
import io.aegisops.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/users")
public class PlatformUserController {

  private final PlatformUserService service;

  public PlatformUserController(PlatformUserService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('platform:user:read')")
  public PlatformUserPage list(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "role", required = false) Set<String> roles,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    PlatformUserQuery query =
        new PlatformUserQuery(
            keyword,
            status == null || status.isBlank() ? null : PlatformUserStatus.from(status),
            roles,
            page,
            pageSize);
    return service.list(query);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('platform:user:read')")
  public PlatformUser findById(@PathVariable String id) {
    return service.findById(id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('platform:user:write')")
  public PlatformUser create(
      @Valid @RequestBody CreatePlatformUserData body,
      @AuthenticationPrincipal UserPrincipal actor) {
    return service.create(body, actor.id());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('platform:user:write')")
  public PlatformUser update(
      @PathVariable String id,
      @Valid @RequestBody UpdatePlatformUserData body,
      @AuthenticationPrincipal UserPrincipal actor) {
    return service.update(id, body, actor.id());
  }

  @PostMapping("/{id}/status")
  @PreAuthorize("hasAuthority('platform:user:status')")
  public PlatformUser changeStatus(
      @PathVariable String id,
      @Valid @RequestBody ChangeUserStatusCommand body,
      @AuthenticationPrincipal UserPrincipal actor) {
    service.changeStatus(id, body, actor.id());
    return service.findById(id);
  }

  @PostMapping("/{id}/reset-password")
  @PreAuthorize("hasAuthority('platform:user:reset-password')")
  public PlatformUser resetPassword(
      @PathVariable String id,
      @Valid @RequestBody ResetPlatformUserPasswordCommand body,
      @AuthenticationPrincipal UserPrincipal actor) {
    service.resetPassword(id, body.newPassword(), actor.id());
    return service.findById(id);
  }

  @PutMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('platform:user:assign-role')")
  public PlatformUser replaceRoles(
      @PathVariable String id,
      @Valid @RequestBody ReplaceUserRolesCommand body,
      @AuthenticationPrincipal UserPrincipal actor) {
    return service.replaceRoles(id, body, actor.id());
  }
}
