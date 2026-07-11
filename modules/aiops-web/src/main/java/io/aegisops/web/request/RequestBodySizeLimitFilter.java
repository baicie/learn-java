package io.aegisops.web.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

  private final RequestBodyLimitProperties properties;
  private final ObjectMapper objectMapper;

  public RequestBodySizeLimitFilter(RequestBodyLimitProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return switch (request.getMethod()) {
      case "POST", "PUT", "PATCH" -> false;
      default -> true;
    };
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long maxBytes = properties.getMaxBodyBytes();

    long contentLength = request.getContentLengthLong();

    if (contentLength > maxBytes) {
      writeRejected(response, maxBytes);
      return;
    }

    HttpServletRequest limited = new LimitedRequest(request, maxBytes);

    try {
      filterChain.doFilter(limited, response);
    } catch (Throwable ex) {
      if (hasTooLargeCause(ex) && !response.isCommitted()) {
        response.reset();
        writeRejected(response, maxBytes);
        return;
      }

      if (ex instanceof IOException ioException) {
        throw ioException;
      }

      if (ex instanceof ServletException servletException) {
        throw servletException;
      }

      if (ex instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }

      throw new ServletException(ex);
    }
  }

  private void writeRejected(HttpServletResponse response, long maxBytes) throws IOException {
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    objectMapper.writeValue(
        response.getWriter(),
        ApiResponse.fail(
            ErrorCode.PAYLOAD_TOO_LARGE.name(),
            "request body exceeds " + maxBytes + " bytes",
            response.getHeader("X-Request-Id")));
  }

  private boolean hasTooLargeCause(Throwable source) {
    Throwable current = source;

    while (current != null) {
      if (current instanceof RequestBodyTooLargeException) {
        return true;
      }
      current = current.getCause();
    }

    return false;
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long maxBytes;

    private LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return new LimitedInputStream(super.getInputStream(), maxBytes);
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
  }

  private static final class LimitedInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long consumed;

    private LimitedInputStream(ServletInputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value >= 0) {
        increment(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = delegate.read(buffer, offset, length);
      if (read > 0) {
        increment(read);
      }
      return read;
    }

    private void increment(long value) {
      consumed += value;
      if (consumed > maxBytes) {
        throw new RequestBodyTooLargeException(maxBytes);
      }
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }
  }
}