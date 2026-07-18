package io.aegisops.integration.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.integration.api.dto.ChangeIngestRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class ChangePayloadMapper {
  private final ObjectMapper json;

  public ChangePayloadMapper(ObjectMapper json) {
    this.json = json;
  }

  public ChangeIngestRequest map(String source, JsonNode node) {
    if (node.hasNonNull("serviceName")) {
      return json.convertValue(node, ChangeIngestRequest.class);
    }
    return switch (source) {
      case "github" -> github(node);
      case "webhook" -> webhook(node);
      default -> throw new IllegalArgumentException("Normalized change payload is required");
    };
  }

  private ChangeIngestRequest github(JsonNode node) {
    JsonNode repository = node.path("repository");
    return request(
        new RequestFields(
            required(node, "id"),
            required(repository, "name"),
            "github_actions",
            text(node.path("workflow_run"), "name", "GitHub Actions run"),
            text(node.path("workflow_run"), "conclusion", null),
            text(node.path("sender"), "login", null)),
        timestamp(node, "created_at"),
        Map.of("sha", text(node.path("workflow_run"), "head_sha", "")),
        text(repository, "html_url", null));
  }

  private ChangeIngestRequest webhook(JsonNode node) {
    String kind = required(node, "kind");
    return switch (kind) {
      case "deployment" ->
          request(
              new RequestFields(
                  required(node, "id"),
                  required(node, "service"),
                  "deployment",
                  text(node, "title", "Deployment"),
                  text(node, "image", null),
                  text(node, "operator", null)),
              timestamp(node, "occurredAt"),
              Map.of("image", text(node, "image", "")),
              null);
      case "helm" ->
          request(
              new RequestFields(
                  required(node, "id"),
                  required(node, "release"),
                  "helm",
                  "Helm release " + required(node, "release"),
                  text(node, "chart", null),
                  text(node, "operator", null)),
              timestamp(node, "occurredAt"),
              Map.of(
                  "chart", text(node, "chart", ""),
                  "namespace", text(node, "namespace", "")),
              null);
      default -> throw new IllegalArgumentException("Unsupported webhook change kind");
    };
  }

  private ChangeIngestRequest request(
      RequestFields fields,
      OffsetDateTime occurredAt,
      Map<String, Object> attributes,
      String repositoryUrl) {
    return new ChangeIngestRequest(
        fields.id(),
        fields.service(),
        fields.type(),
        fields.title(),
        fields.description(),
        fields.operator(),
        "medium",
        occurredAt,
        attributes,
        null,
        repositoryUrl,
        null,
        List.of());
  }

  private OffsetDateTime timestamp(JsonNode node, String field) {
    return OffsetDateTime.parse(required(node, field));
  }

  private String required(JsonNode node, String field) {
    String value = text(node, field, null);
    if (value == null) throw new IllegalArgumentException(field + " is required");
    return value;
  }

  private String text(JsonNode node, String field, String fallback) {
    String value = node.path(field).asText("").trim();
    return value.isEmpty() ? fallback : value;
  }

  private record RequestFields(
      String id, String service, String type, String title, String description, String operator) {}
}
