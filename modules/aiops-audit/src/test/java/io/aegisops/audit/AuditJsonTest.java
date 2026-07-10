package io.aegisops.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditJsonTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AuditJson json = new AuditJson(mapper);

  @Test
  void normalizeObjectShouldReturnEmptyObjectForBlank() {
    assertThat(json.normalizeObject(null, "beforeJson")).isEqualTo("{}");
    assertThat(json.normalizeObject("", "beforeJson")).isEqualTo("{}");
    assertThat(json.normalizeObject("   ", "beforeJson")).isEqualTo("{}");
  }

  @Test
  void normalizeObjectShouldRejectInvalidJson() {
    assertThatThrownBy(() -> json.normalizeObject("{invalid", "beforeJson"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalizeObjectShouldRejectArray() {
    assertThatThrownBy(() -> json.normalizeObject("[]", "beforeJson"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON object");
  }

  @Test
  void normalizeObjectShouldRejectString() {
    assertThatThrownBy(() -> json.normalizeObject("\"hello\"", "afterJson"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a JSON object");
  }

  @Test
  void writeShouldRejectNonObject() {
    assertThatThrownBy(() -> json.write(List.of(1, 2, 3)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("audit snapshot must be a JSON object");
  }

  @Test
  void writeShouldSerializeValue() {
    String out = json.write(Map.of("a", 1, "b", "x"));
    assertThat(out).contains("\"a\":1");
    assertThat(out).contains("\"b\":\"x\"");
  }

  @Test
  void redactShouldMaskSensitiveKeys() {
    String out = json.write(Map.of("password", "secret", "name", "alice"));
    assertThat(out).contains("\"password\":\"***\"");
    assertThat(out).doesNotContain("\"secret\"");
    assertThat(out).contains("\"name\":\"alice\"");
  }

  @Test
  void redactShouldMaskSensitiveKeysCaseInsensitive() {
    String out = json.write(Map.of("Authorization", "Bearer xxx"));
    assertThat(out).contains("\"Authorization\":\"***\"");
    assertThat(out).doesNotContain("Bearer xxx");
  }

  @Test
  void redactShouldMaskNestedSensitiveKeys() {
    String out = json.write(Map.of("outer", Map.of("clientSecret", "value", "safe", "ok")));
    assertThat(out).contains("\"clientSecret\":\"***\"");
    assertThat(out).contains("\"safe\":\"ok\"");
  }

  @Test
  void diffShouldDetectFieldChange() {
    Map<String, Object> before = Map.of("title", "old");
    Map<String, Object> after = Map.of("title", "new");
    var changes = json.diff(before, after);
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).path()).isEqualTo("/title");
    assertThat(changes.get(0).beforeValue().asText()).isEqualTo("old");
    assertThat(changes.get(0).afterValue().asText()).isEqualTo("new");
  }

  @Test
  void diffShouldReportEqualAsEmpty() {
    Map<String, Object> before = Map.of("a", 1);
    Map<String, Object> after = Map.of("a", 1);
    assertThat(json.diff(before, after)).isEmpty();
  }

  @Test
  void diffShouldHandleNullBeforeOrAfter() {
    Map<String, Object> after = Map.of("a", 1);
    assertThat(json.diff(null, after)).hasSize(1);
    assertThat(json.diff(after, null)).hasSize(1);
  }

  @Test
  void sensitiveFieldChangeShouldRemainVisible() {
    var changes = json.diff(Map.of("password", "old-password"), Map.of("password", "new-password"));

    assertThat(changes)
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.path()).isEqualTo("/password");
              assertThat(change.beforeValue().asText()).isEqualTo("***");
              assertThat(change.afterValue().asText()).isEqualTo("***");
            });
  }

  @Test
  void detailShouldIncludeChangesAndAttributes() throws Exception {
    String out =
        json.detail(Map.of("templateId", "t1"), Map.of("title", "old"), Map.of("title", "new"));
    JsonNode detail = mapper.readTree(out);
    assertThat(detail.get("templateId").asText()).isEqualTo("t1");
    assertThat(detail.get("changes").isArray()).isTrue();
    assertThat(detail.get("changes").get(0).get("path").asText()).isEqualTo("/title");
    assertThat(detail.get("changesTruncated").asBoolean()).isFalse();
  }

  @Test
  void detailShouldMarkTruncatedChanges() throws Exception {
    Map<String, Object> before = new LinkedHashMap<>();
    Map<String, Object> after = new LinkedHashMap<>();
    for (int i = 0; i < 250; i++) {
      before.put("field" + i, 0);
      after.put("field" + i, 1);
    }

    JsonNode detail = mapper.readTree(json.detail(Map.of(), before, after));

    assertThat(detail.get("changes").size()).isEqualTo(200);
    assertThat(detail.get("changesTruncated").asBoolean()).isTrue();
  }
}
