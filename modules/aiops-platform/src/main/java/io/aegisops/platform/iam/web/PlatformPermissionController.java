package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.service.PlatformPermissionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/permissions")
public class PlatformPermissionController {

  private final PlatformPermissionService service;

  public PlatformPermissionController(PlatformPermissionService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('platform:role:read')")
  public List<PermissionModuleResponse> list() {
    Map<String, List<PermissionResponse>> modules = new LinkedHashMap<>();
    for (PermissionDefinition permission : service.list()) {
      modules
          .computeIfAbsent(permission.moduleCode(), ignored -> new ArrayList<>())
          .add(PermissionResponse.from(permission));
    }
    return modules.entrySet().stream()
        .map(
            entry ->
                new PermissionModuleResponse(
                    entry.getKey(), moduleName(entry.getKey()), entry.getValue()))
        .toList();
  }

  private static String moduleName(String code) {
    return switch (code) {
      case "platform-user" -> "用户管理";
      case "platform-role" -> "权限管理";
      case "platform" -> "平台管理";
      case "work-record" -> "工作记录";
      default -> code;
    };
  }

  public record PermissionModuleResponse(
      String moduleCode, String moduleName, List<PermissionResponse> children) {}

  public record PermissionResponse(
      String code,
      String moduleCode,
      String moduleName,
      String name,
      String description,
      String riskLevel,
      Set<String> dependencies) {

    static PermissionResponse from(PermissionDefinition permission) {
      return new PermissionResponse(
          permission.code(),
          permission.moduleCode(),
          PlatformPermissionController.moduleName(permission.moduleCode()),
          permission.name(),
          permission.description(),
          permission.risk().name().toLowerCase(Locale.ROOT),
          permission.dependencies());
    }
  }
}
