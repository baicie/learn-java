package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Decides whether an incoming HTTP request targets the bundled web console (static assets or
 * browser-side SPA routes) versus a real backend endpoint.
 *
 * <p>The same predicates are reused in two places:
 *
 * <ul>
 *   <li>Spring Security matchers in {@code SecurityConfig} keep the console reachable without a JWT
 *       while {@code /api/**} keeps requiring authentication.
 *   <li>{@link io.aegisops.server.console.ConsoleSpaForwardConfiguration} forwards unmatched GET
 *       requests to {@code /index.html} for SPA routing.
 * </ul>
 *
 * <p>A request is treated as a console request when it is a {@code GET} (or {@code HEAD}) whose
 * normalized path either starts with a known backend prefix (in which case it is <em>not</em> a
 * console request) or qualifies as a console asset or console page.
 */
public final class ConsolePaths {
  private static final List<String> BACKEND_PREFIXES =
      List.of("/api", "/actuator", "/v3/api-docs", "/swagger-ui", "/swagger-ui.html", "/internal");

  private ConsolePaths() {}

  /**
   * Convenience matcher used by {@code SecurityConfig#securityFilterChain}. Returns {@code true}
   * for both console asset and console page requests.
   */
  public static boolean isConsoleRequest(HttpServletRequest request) {
    return isConsoleAssetRequest(request) || isConsolePageRequest(request);
  }

  /**
   * Whether the request should be served as the SPA shell {@code index.html}. A request qualifies
   * when it is a {@code GET}/{@code HEAD} that does not hit a backend prefix, has no file
   * extension, and either is the root path or advertises {@code Accept: text/html}.
   */
  public static boolean isConsolePageRequest(HttpServletRequest request) {
    if (!isGetOrHead(request)) {
      return false;
    }

    String path = normalizedPath(request);
    if (isBackendPath(path) || hasFileExtension(path)) {
      return false;
    }

    if ("/".equals(path)) {
      return true;
    }

    return acceptsHtml(request);
  }

  /**
   * Whether the request targets a static asset emitted by Vite. A request qualifies when it is a
   * {@code GET}/{@code HEAD} that does not hit a backend prefix and either lives under {@code
   * /assets/} or carries a file extension (e.g. {@code /favicon.ico}, {@code /robots.txt}).
   */
  public static boolean isConsoleAssetRequest(HttpServletRequest request) {
    if (!isGetOrHead(request)) {
      return false;
    }

    String path = normalizedPath(request);
    if (isBackendPath(path)) {
      return false;
    }

    return path.startsWith("/assets/") || hasFileExtension(path);
  }

  /**
   * Whether {@code path} targets a known backend endpoint group. Performs an exact-or-prefix match
   * against the configured backend prefixes.
   */
  public static boolean isBackendPath(String path) {
    if (path == null) {
      return false;
    }

    for (String prefix : BACKEND_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
    }
    return false;
  }

  static String normalizedPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();

    if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }

    if (path == null || path.isBlank()) {
      return "/";
    }

    return path.startsWith("/") ? path : "/" + path;
  }

  private static boolean isGetOrHead(HttpServletRequest request) {
    String method = request.getMethod();
    return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
  }

  private static boolean acceptsHtml(HttpServletRequest request) {
    String accept = request.getHeader(HttpHeaders.ACCEPT);
    return accept == null || accept.contains("text/html");
  }

  private static boolean hasFileExtension(String path) {
    int lastSlash = path.lastIndexOf('/');
    int lastDot = path.lastIndexOf('.');
    return lastDot > lastSlash;
  }
}
