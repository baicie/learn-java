package io.aegisops.platform;

import io.aegisops.common.api.ApiResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/navigation")
public class PlatformNavigationController {
  private final PlatformMenuRepository repository;

  public PlatformNavigationController(PlatformMenuRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/menus")
  public ApiResponse<List<PlatformMenuItem>> menus(Authentication authentication) {
    List<String> authorities =
        authentication == null
            ? List.of()
            : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();
    return ApiResponse.ok(repository.listEnabled(authorities));
  }
}
