package io.aegisops.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditJsonTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AuditJson json = new AuditJson(mapper);

  @Test
  void normalizeJsonShouldReturnEmptyObjectForBlank() {
    assertThat(json.normalizeJson(null)).isEqualTo("{}");
    assertThat(json.normalizeJson("")).isEqualTo("{}");
    assertThat(json.normalizeJson("   ")).isEqualTo("{}");
  }

  @Test
  void normalizeJsonShouldRejectInvalidJson() {
    assertThatThrownBy(() -> json.normalizeJson("{invalid"))
        .isInstanceOf(IllegalArgumentException.class);
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
  void diffShouldMaskSensitiveKeysInBeforeAndAfter() {
    Map<String, Object> before = Map.of("password", "old");
    Map<String, Object> after = Map.of("password", "new");
    // Both before/after are redacted to "***" before diff, so they appear equal.
    // The real change stays in the raw event, but the diff entry is suppressed to
    // avoid leaking "the password changed but we won't tell you from what".
    var changes = json.diff(before, after);
    assertThat(changes).isEmpty();
  }

  @Test
  void diffShouldStillDetectChangeWhenOneSideIsSensitiveAndOtherIsNot() {
    Map<String, Object> before = Map.of("password", "old", "name", "alice");
    Map<String, Object> after = Map.of("password", "new", "name", "alice");
    var changes = json.diff(before, after);
    // name unchanged, password redacted to "***" on both sides -> no diff
    assertThat(changes).isEmpty();
  }

  @Test
  void detailShouldIncludeChangesAndAttributes() {
    String out =
        json.detail(Map.of("templateId", "t1"), Map.of("title", "old"), Map.of("title", "new"));
    assertThat(out).contains("\"templateId\":\"t1\"");
    assertThat(out).contains("\"changes\"");
    assertThat(out).contains("/title");
  }
}
