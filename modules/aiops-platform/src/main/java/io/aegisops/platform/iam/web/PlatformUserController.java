package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.domain.CreatePlatformUserData;
import io.aegisops.platform.iam.domain.ChangeUserStatusCommand;
import io.aegisops.platform.iam.domain.PlatformUser;
import io.aegisops.platform.iam.domain.PlatformUserPage;
import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import io.aegisops.platform.iam.domain.ReplaceUserRolesCommand;
import io.aegisops.platform.iam.domain.UpdatePlatformUserData;
import io.aegisops.platform.iam.service.PlatformUserService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
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
  public PlatformUser findById(@PathVariable String id) {
    return service.findById(id);
  }

  @PostMapping
  public PlatformUser create(@Valid @RequestBody CreatePlatformUserData body) {
    String actor = currentActor();
    return service.create(body, actor);
  }

  @PutMapping("/{id}")
  public PlatformUser update(
      @PathVariable String id, @Valid @RequestBody UpdatePlatformUserData body) {
    return service.update(id, body, currentActor());
  }

  @PostMapping("/{id}/status")
  public Map<String, Object> changeStatus(
      @PathVariable String id, @Valid @RequestBody ChangeUserStatusCommand body) {
    service.changeStatus(id, body, currentActor());
    return Map.of("status", "ok", "id", id);
  }

  @PostMapping("/{id}/reset-password")
  public Map<String, Object> resetPassword(
      @PathVariable String id, @Valid @RequestBody Map<String, String> body) {
    service.resetPassword(id, body.getOrDefault("newPassword", ""), currentActor());
    return Map.of("status", "ok", "id", id);
  }

  @PostMapping("/{id}/roles")
  public PlatformUser replaceRoles(
      @PathVariable String id, @Valid @RequestBody ReplaceUserRolesCommand body) {
    return service.replaceRoles(id, body, currentActor());
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
      // ignore - the UserPrincipal may not be wired for tests
    }
    return "system";
  }
}