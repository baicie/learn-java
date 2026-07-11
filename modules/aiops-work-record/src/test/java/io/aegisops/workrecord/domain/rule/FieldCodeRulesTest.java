package io.aegisops.workrecord.domain.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldCodeRulesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "name",
        "field1",
        "field_name",
        "A",
        "a123_b456",
        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghij12"
      })
  void shouldAcceptValidFieldCodes(String fieldCode) {
    assertThatCode(() -> FieldCodeRules.validate(fieldCode)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1field",
        "_field",
        "field-name",
        "field.name",
        "字段",
        "field name",
        "a/b",
        "a'b",
        "a\"b"
      })
  void shouldRejectInvalidFieldCodes(String fieldCode) {
    assertThatThrownBy(() -> FieldCodeRules.validate(fieldCode))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "id",
        "tenant_id",
        "template_id",
        "template_version_id",
        "title",
        "status",
        "owner_id",
        "creator_id",
        "record_time",
        "builtin_data_json",
        "custom_data_json",
        "row_version",
        "created_at",
        "updated_at",
        "deleted_at"
      })
  void shouldRejectReservedFieldCodes(String fieldCode) {
    assertThatThrownBy(() -> FieldCodeRules.validate(fieldCode))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved fieldCode");
  }

  @Test
  void shouldRejectMoreThan64Characters() {
    String value = "a" + "b".repeat(64);

    assertThatThrownBy(() -> FieldCodeRules.validate(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectBlankFieldCode() {
    assertThatThrownBy(() -> FieldCodeRules.validate(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fieldCode is required");

    assertThatThrownBy(() -> FieldCodeRules.validate("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fieldCode is required");

    assertThatThrownBy(() -> FieldCodeRules.validate(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fieldCode is required");
  }
}
