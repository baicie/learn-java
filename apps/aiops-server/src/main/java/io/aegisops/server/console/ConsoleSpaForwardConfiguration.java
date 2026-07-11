package io.aegisops.server.console;

import io.aegisops.security.ConsolePaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wires an SPA fallback filter that forwards unmatched browser-side GET requests to {@code
 * /index.html} so React Router can take over the route. The filter is registered with the lowest
 * precedence to run after the Spring Security chain and other application filters.
 *
 * <p>The forward is only triggered when {@code classpath:/static/index.html} exists, which means
 * the console dist was bundled into the jar via the {@code with-console} Maven profile. In plain
 * backend builds the filter is a no-op.
 */
@Configuration
public class ConsoleSpaForwardConfiguration {
  @Bean
  public FilterRegistrationBean<OncePerRequestFilter> consoleSpaForwardFilter(
      ResourceLoader resourceLoader) {
    FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();

    registration.setName("consoleSpaForwardFilter");
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
    registration.setFilter(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              @NonNull HttpServletRequest request,
              @NonNull HttpServletResponse response,
              @NonNull FilterChain filterChain)
              throws ServletException, IOException {
            if (shouldForward(request, consoleIndexExists(resourceLoader))) {
              request.getRequestDispatcher("/index.html").forward(request, response);
              return;
            }

            filterChain.doFilter(request, response);
          }
        });

    return registration;
  }

  static boolean shouldForward(HttpServletRequest request, boolean consoleIndexExists) {
    return consoleIndexExists && ConsolePaths.isConsolePageRequest(request);
  }

  private static boolean consoleIndexExists(ResourceLoader resourceLoader) {
    return resourceLoader.getResource("classpath:/static/index.html").exists();
  }
}
