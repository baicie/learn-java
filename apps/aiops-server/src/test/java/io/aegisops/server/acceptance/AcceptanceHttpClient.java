package io.aegisops.server.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

final class AcceptanceHttpClient {

  private final TestRestTemplate rest;
  private final ObjectMapper objectMapper;

  AcceptanceHttpClient(TestRestTemplate rest, ObjectMapper objectMapper) {
    this.rest = rest;
    this.objectMapper = objectMapper;
  }

  String login(String username, String password) {
    ResponseEntity<JsonNode> response =
        rest.postForEntity(
            "/api/auth/login", Map.of("username", username, "password", password), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = requireBody(response);
    assertThat(body.path("success").asBoolean()).isTrue();

    String token = body.path("data").path("token").asText();

    assertThat(token).isNotBlank();
    return token;
  }

  JsonNode getData(String path, String token) {
    return data(getRaw(path, token, new LinkedMultiValueMap<>()));
  }

  JsonNode getData(String path, String token, MultiValueMap<String, String> query) {
    return data(getRaw(path, token, query));
  }

  JsonNode postData(String path, String token, Object body) {
    return data(exchangeJson(path, token, HttpMethod.POST, body));
  }

  JsonNode putData(String path, String token, Object body) {
    return data(exchangeJson(path, token, HttpMethod.PUT, body));
  }

  JsonNode deleteData(String path, String token) {
    return data(exchangeJson(path, token, HttpMethod.DELETE, null));
  }

  ResponseEntity<JsonNode> getRaw(String path, String token, MultiValueMap<String, String> query) {
    URI uri = UriComponentsBuilder.fromPath(path).queryParams(query).build().encode().toUri();

    return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
  }

  ResponseEntity<JsonNode> postRaw(String path, String token, Object body) {
    return exchangeJson(path, token, HttpMethod.POST, body);
  }

  ResponseEntity<byte[]> postCsv(String path, String token, Object body) {
    HttpHeaders headers = headers(token);
    headers.setAccept(List.of(new MediaType("text", "csv")));

    return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
  }

  MultiValueMap<String, String> query(String... values) {
    if (values.length % 2 != 0) {
      throw new IllegalArgumentException("query values must be key/value pairs");
    }

    LinkedMultiValueMap<String, String> result = new LinkedMultiValueMap<>();

    for (int index = 0; index < values.length; index += 2) {
      result.add(values[index], values[index + 1]);
    }

    return result;
  }

  String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize acceptance payload", ex);
    }
  }

  private ResponseEntity<JsonNode> exchangeJson(
      String path, String token, HttpMethod method, Object body) {
    return rest.exchange(path, method, new HttpEntity<>(body, headers(token)), JsonNode.class);
  }

  private JsonNode data(ResponseEntity<JsonNode> response) {
    assertThat(response.getStatusCode())
        .as("response body: %s", response.getBody())
        .isEqualTo(HttpStatus.OK);

    JsonNode body = requireBody(response);

    assertThat(body.path("success").asBoolean()).isTrue();

    return body.path("data");
  }

  private JsonNode requireBody(ResponseEntity<JsonNode> response) {
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private HttpHeaders headers(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    if (token != null && !token.isBlank()) {
      headers.setBearerAuth(token);
    }

    return headers;
  }
}
