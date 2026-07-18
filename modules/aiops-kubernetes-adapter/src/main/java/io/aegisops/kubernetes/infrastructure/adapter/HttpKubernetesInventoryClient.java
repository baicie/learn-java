package io.aegisops.kubernetes.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.kubernetes.application.KubernetesInventoryClient;
import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.kubernetes.domain.model.KubernetesResource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpKubernetesInventoryClient implements KubernetesInventoryClient {
  private static final Map<String, String> PATHS = paths();
  private final KubernetesConfig config;
  private final HttpClient httpClient;
  private final KubernetesInventoryParser parser;

  public HttpKubernetesInventoryClient(KubernetesConfig config, ObjectMapper objectMapper) {
    this(
        config,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds())).build(),
        objectMapper);
  }

  HttpKubernetesInventoryClient(
      KubernetesConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
    this.config = config;
    this.httpClient = httpClient;
    this.parser = new KubernetesInventoryParser(objectMapper);
  }

  @Override
  public String version() {
    try {
      return new ObjectMapper().readTree(get("/version")).path("gitVersion").asText();
    } catch (Exception exception) {
      throw new IllegalStateException("Kubernetes version response is invalid", exception);
    }
  }

  @Override
  public List<KubernetesResource> listInventory() {
    List<KubernetesResource> resources = new ArrayList<>();
    PATHS.forEach((kind, path) -> resources.addAll(parser.parse(kind, get(path))));
    return List.copyOf(resources);
  }

  private String get(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(endpoint() + path))
              .timeout(Duration.ofSeconds(config.timeoutSeconds()))
              .header("Accept", "application/json")
              .header("Authorization", "Bearer " + config.apiToken())
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException("Kubernetes API returned HTTP " + response.statusCode());
      }
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kubernetes API request interrupted", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Kubernetes API request failed", exception);
    }
  }

  private String endpoint() {
    String endpoint = config.endpoint().trim();
    return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  private static Map<String, String> paths() {
    Map<String, String> paths = new LinkedHashMap<>();
    paths.put("Node", "/api/v1/nodes");
    paths.put("Namespace", "/api/v1/namespaces");
    paths.put("Pod", "/api/v1/pods");
    paths.put("Service", "/api/v1/services");
    paths.put("Deployment", "/apis/apps/v1/deployments");
    paths.put("StatefulSet", "/apis/apps/v1/statefulsets");
    paths.put("DaemonSet", "/apis/apps/v1/daemonsets");
    paths.put("Ingress", "/apis/networking.k8s.io/v1/ingresses");
    return Map.copyOf(paths);
  }
}
