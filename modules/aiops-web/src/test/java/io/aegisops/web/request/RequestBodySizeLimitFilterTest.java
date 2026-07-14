package io.aegisops.web.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 请求体大小限制必须： 1) 拦截超过 Content-Length 上限的请求，立即返回 413 + ApiResponse； 2) 拦截 chunked 请求（无
 * Content-Length），读到上限后抛 RequestBodyTooLargeException 触发 413； 3) 让小于上限的请求体透传到后续过滤器。
 */
class RequestBodySizeLimitFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void rejectsRequestWithContentLengthAboveLimit() throws Exception {
    RequestBodyLimitProperties properties = new RequestBodyLimitProperties();
    properties.setMaxBodyBytes(1024);

    RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(properties, objectMapper);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/work-record/records");
    request.setContentType("application/json");
    request.setContent(new byte[2048]);

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, resp) -> {
          throw new AssertionError("filter chain must not continue for oversized body");
        });

    assertThat(response.getStatus()).isEqualTo(413);
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("success").asBoolean()).isFalse();
    assertThat(body.path("errorCode").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
    assertThat(body.path("message").asText()).contains("1024");
  }

  @Test
  void rejectsStreamingBodyWithoutContentLength() throws Exception {
    RequestBodyLimitProperties properties = new RequestBodyLimitProperties();
    properties.setMaxBodyBytes(1024);

    RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(properties, objectMapper);

    MockHttpServletRequest request =
        new MockHttpServletRequest() {
          @Override
          public int getContentLength() {
            return -1;
          }

          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };

    request.setMethod("POST");
    request.setRequestURI("/api/work-record/records");
    request.setContentType("application/json");
    request.setContent(new byte[2048]);

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, resp) -> {
          try {
            req.getInputStream().readAllBytes();
          } catch (Exception ex) {
            throw new ServletException(ex);
          }
        });

    // 即便后续过滤器抛错，Filter 必须把响应重置为 413 + ApiResponse JSON。
    assertThat(response.getStatus()).isEqualTo(413);
    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("errorCode").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
  }

  @Test
  void allowsRequestBelowLimitToPassThrough() throws Exception {
    RequestBodyLimitProperties properties = new RequestBodyLimitProperties();
    properties.setMaxBodyBytes(1024);

    RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(properties, objectMapper);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/work-record/records");
    request.setContentType("application/json");
    request.setContent("{\"hello\":\"world\"}".getBytes());

    MockHttpServletResponse response = new MockHttpServletResponse();

    final boolean[] invoked = {false};

    filter.doFilter(
        request,
        response,
        (req, resp) -> {
          invoked[0] = true;
        });

    assertThat(invoked[0]).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void getMethodDoesNotInvokeFilter() throws Exception {
    RequestBodyLimitProperties properties = new RequestBodyLimitProperties();
    properties.setMaxBodyBytes(1024);

    RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(properties, objectMapper);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/work-record/records");

    MockHttpServletResponse response = new MockHttpServletResponse();

    final boolean[] invoked = {false};

    filter.doFilter(request, response, (req, resp) -> invoked[0] = true);

    assertThat(invoked[0]).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
