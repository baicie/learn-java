package io.aegisops.security;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtTokenService tokenService;
  private final UserService userService;

  public JwtAuthenticationFilter(JwtTokenService tokenService, UserService userService) {
    this.tokenService = tokenService;
    this.userService = userService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      try {
        JwtTokenService.JwtClaims claims = tokenService.verify(auth.substring("Bearer ".length()));
        UserAccount user = userService.getById(claims.userId());
        UserPrincipal principal =
            new UserPrincipal(
                user.id(), user.tenantId(), user.username(), user.displayName(), user.roles());
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
        TenantContext.setTenantId(user.tenantId());
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
