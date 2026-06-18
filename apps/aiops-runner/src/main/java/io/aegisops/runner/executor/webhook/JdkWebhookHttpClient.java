package io.aegisops.runner.executor.webhook;

import io.aegisops.common.exception.AppException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JdkWebhookHttpClient implements WebhookHttpClient {
  private final HttpClient client;

  public JdkWebhookHttpClient() {
    this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override
  public WebhookHttpResponse send(WebhookHttpRequest request) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder().uri(request.uri()).timeout(request.timeout());

      for (Map.Entry<String, String> entry : request.headers().entrySet()) {
        builder.header(entry.getKey(), entry.getValue());
      }

      String method = request.method().toUpperCase();
      if ("GET".equals(method)) {
        builder.GET();
      } else {
        builder.method(
            method,
            HttpRequest.BodyPublishers.ofString(request.body() == null ? "" : request.body()));
      }

      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

      Map<String, String> headers =
          response.headers().map().entrySet().stream()
              .collect(
                  Collectors.toMap(Map.Entry::getKey, item -> String.join(",", item.getValue())));

      return new WebhookHttpResponse(response.statusCode(), headers, response.body());
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_HTTP_SEND_FAILED", "Webhook HTTP request failed");
    }
  }
}
