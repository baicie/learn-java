package io.aegisops.kubernetes.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HttpKubernetesInventoryClientTest {
  @Test
  void sendsBearerTokenOnlyInAuthorizationHeader() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/version",
        exchange -> {
          assertEquals(
              "Bearer inventory-secret", exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "{\"gitVersion\":\"v1.33.0\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var config =
          new KubernetesConfig(
              "http://127.0.0.1:" + server.getAddress().getPort(), "inventory-secret", 5);
      var client = new HttpKubernetesInventoryClient(config, new ObjectMapper());

      assertEquals("v1.33.0", client.version());
    } finally {
      server.stop(0);
    }
  }
}
