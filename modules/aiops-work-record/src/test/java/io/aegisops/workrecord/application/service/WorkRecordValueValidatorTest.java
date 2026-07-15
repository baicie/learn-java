package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.support.WorkRecordFixtures;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 值校验服务契约：覆盖类型、静态选项、字典选项、多选、用户、禁用字段、保留字段。
 *
 * <p>测试名描述长期不变的业务契约，与具体 Phase 编号无关。
 */
class WorkRecordValueValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final WorkRecordDictionaryPort dictionaryPort = mock(WorkRecordDictionaryPort.class);

  private final WorkRecordUserPort userPort = mock(WorkRecordUserPort.class);

  private final WorkRecordValueValidator validator =
      new WorkRecordValueValidator(objectMapper, dictionaryPort, userPort);

  private WorkRecordField selectDictField(String code, String dictCode) {
    return new WorkRecordField(
        "f1",
        TENANT_ID,
        TEMPLATE_ID,
        VERSION_ID,
        "优先级",
        code,
        FieldType.SELECT,
        true,
        null,
        OptionSource.DICT,
        dictCode,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField staticSelectField(String code, String optionsJson) {
    return new WorkRecordField(
        "f1",
        TENANT_ID,
        TEMPLATE_ID,
        VERSION_ID,
        "优先级",
        code,
        FieldType.SELECT,
        false,
        null,
        OptionSource.STATIC,
        null,
        optionsJson,
        ".properties." + code,
        true,
        true,
        true,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField textField(String code, boolean required) {
    return new WorkRecordField(
        "f1",
        TENANT_ID,
        TEMPLATE_ID,
        VERSION_ID,
        "内容",
        code,
        FieldType.TEXTAREA,
        required,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField disabledField(String code) {
    return new WorkRecordField(
        "f1",
        TENANT_ID,
        TEMPLATE_ID,
        VERSION_ID,
        "旧字段",
        code,
        FieldType.TEXT,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        1,
        false,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecordField staticMultiSelectField(String code, String optionsJson) {
    return new WorkRecordField(
        "f1",
        TENANT_ID,
        TEMPLATE_ID,
        VERSION_ID,
        "标签",
        code,
        FieldType.MULTI_SELECT,
        false,
        null,
        OptionSource.STATIC,
        null,
        optionsJson,
        ".properties." + code,
        true,
        true,
        true,
        false,
        0,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  @Test
  void shouldAcceptEmptyCustomData() {
    assertThatCode(() -> validator.validate(TENANT_ID, VERSION_ID, List.of(), "{}"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUnknownField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID,
                    VERSION_ID,
                    List.of(textField("count", false)),
                    "{\"unknown\":\"v\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown field: unknown");
  }

  @Test
  void shouldRejectInvalidFieldCodeInCustomData() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID,
                    VERSION_ID,
                    List.of(textField("content", false)),
                    "{\"bad-key\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void reservedCustomDataKeyMustBeRejectedBeforePersistence() {
    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(), "{\"title\":\"bad\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved fieldCode");
  }

  @Test
  void shouldRejectDisabledFieldWrite() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID,
                    VERSION_ID,
                    List.of(disabledField("oldField")),
                    "{\"oldField\":\"x\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is disabled: oldField");
  }

  @Test
  void shouldRejectMissingRequiredField() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(textField("content", true)), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: content");
  }

  @Test
  void draftMayOmitRequiredFields() {
    assertThatCode(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(textField("content", true)), "{}", false))
        .doesNotThrowAnyException();
  }

  @Test
  void completedRecordMustContainRequiredFields() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(textField("content", true)), "{}", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: content");
  }

  @Test
  void shouldRejectNumberAsString() {
    var field =
        WorkRecordFixtures.field(
            VERSION_ID,
            "cost",
            FieldType.NUMBER,
            OptionSource.STATIC,
            null,
            "[]",
            false,
            true,
            true,
            true);

    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"cost\":\"1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be number: cost");
  }

  @Test
  void shouldRejectInvalidDate() {
    var field =
        WorkRecordFixtures.field(
            VERSION_ID,
            "day",
            FieldType.DATE,
            OptionSource.STATIC,
            null,
            "[]",
            false,
            true,
            true,
            true);

    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(field), "{\"day\":\"2026/01/01\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be ISO date: day");
  }

  @Test
  void shouldRejectDatetimeWithoutOffset() {
    var field =
        WorkRecordFixtures.field(
            VERSION_ID,
            "startedAt",
            FieldType.DATETIME,
            OptionSource.STATIC,
            null,
            "[]",
            false,
            true,
            true,
            true);

    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID,
                    VERSION_ID,
                    List.of(field),
                    "{\"startedAt\":\"2026-01-01T10:00:00\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must be ISO offset datetime: startedAt");
  }

  @Test
  void shouldValidateStaticSelectOptions() {
    var priority = staticSelectField("priority", "[\"P0\",\"P1\"]");
    validator.validate(TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P1\"}");
  }

  @Test
  void shouldRejectStaticSelectOptionNotAllowed() {
    var priority = staticSelectField("priority", "[\"P0\",\"P1\"]");

    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P2\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field option is not allowed: priority/P2");
  }

  @Test
  void shouldRejectStaticSelectWithoutOptions() {
    var priority = staticSelectField("priority", "[]");

    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("static options are required: priority");
  }

  @Test
  void shouldValidateDictSelectValue() {
    var priority = selectDictField("priority", "record_priority");
    validator.validate(TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P1\"}");
    verify(dictionaryPort).requireEnabledItem(TENANT_ID, "record_priority", "P1");
  }

  @Test
  void disabledDictionaryValueMustBeRejected() {
    var priority = selectDictField("priority", "priority");

    Mockito.doThrow(new IllegalArgumentException("dict item not found or disabled: priority/P2"))
        .when(dictionaryPort)
        .requireEnabledItem(TENANT_ID, "priority", "P2");

    assertThatThrownBy(
            () ->
                validator.validate(
                    TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"P2\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found or disabled");
  }

  @Test
  void shouldValidateStaticMultiSelectOptions() {
    var tags = staticMultiSelectField("tags", "[{\"label\":\"A\",\"value\":\"a\"}]");
    validator.validate(TENANT_ID, VERSION_ID, List.of(tags), "{\"tags\":[\"a\"]}");
  }

  @Test
  void shouldRejectMultiSelectNonStringItem() {
    var tags = staticMultiSelectField("tags", "[\"a\"]");

    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(tags), "{\"tags\":[1]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multi_select item must be string: tags");
  }

  @Test
  void shouldValidateDictMultiSelectValues() {
    var field =
        WorkRecordFixtures.field(
            VERSION_ID,
            "tags",
            FieldType.MULTI_SELECT,
            OptionSource.DICT,
            "tag_dict",
            "[]",
            false,
            true,
            true,
            true);

    validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"tags\":[\"a\",\"b\"]}");

    verify(dictionaryPort).requireEnabledItem(TENANT_ID, "tag_dict", "a");
    verify(dictionaryPort).requireEnabledItem(TENANT_ID, "tag_dict", "b");
  }

  @Test
  void shouldValidateUserField() {
    var user =
        WorkRecordFixtures.field(
            VERSION_ID,
            "assignee",
            FieldType.USER,
            OptionSource.STATIC,
            null,
            "[]",
            false,
            true,
            true,
            true);

    validator.validate(TENANT_ID, VERSION_ID, List.of(user), "{\"assignee\":\"u1\"}");
    verify(userPort).requireActiveUser(TENANT_ID, "u1");
  }

  @Test
  void shouldRejectFieldTenantMismatch() {
    var field =
        new WorkRecordField(
            "f1",
            "tenant-2",
            TEMPLATE_ID,
            VERSION_ID,
            "内容",
            "content",
            FieldType.TEXTAREA,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.content",
            true,
            true,
            true,
            false,
            0,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThatThrownBy(() -> validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field tenant mismatch: content");
  }

  @Test
  void shouldRejectBlankStringForOptionalSelect() {
    var priority = staticSelectField("priority", "[\"P0\",\"P1\"]");

    assertThatThrownBy(
            () ->
                validator.validate(TENANT_ID, VERSION_ID, List.of(priority), "{\"priority\":\"\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field must not be blank: priority");
  }

  @Test
  void shouldRejectDictOptionSourceOnTextField() {
    var field =
        WorkRecordFixtures.field(
            VERSION_ID,
            "priority",
            FieldType.TEXT,
            OptionSource.DICT,
            "record_priority",
            "[]",
            false,
            true,
            true,
            true);

    assertThatThrownBy(
            () ->
                validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dict optionSource is only allowed");
  }

  @Test
  void shouldRejectTextThatViolatesVersionedLengthRule() {
    var original = textField("summary", false);
    var field = withValidation(original, "{\"minLength\":3,\"maxLength\":10}");

    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"summary\":\"ab\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is shorter than minLength: summary");
  }

  @Test
  void shouldRejectNumberThatViolatesVersionedRangeRule() {
    var original =
        WorkRecordFixtures.field(
            VERSION_ID,
            "hours",
            FieldType.NUMBER,
            OptionSource.STATIC,
            null,
            "[]",
            false,
            true,
            false,
            true);
    var field = withValidation(original, "{\"minimum\":0,\"maximum\":24}");

    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"hours\":25}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field is greater than maximum: hours");
  }

  @Test
  void shouldAllowEmptyArrayForOptionalMultiSelect() {
    var tags = staticMultiSelectField("tags", "[\"a\",\"b\"]");
    validator.validate(TENANT_ID, VERSION_ID, List.of(tags), "{\"tags\":[]}");
  }

  @Test
  void shouldRejectEmptyArrayForRequiredMultiSelect() {
    var tags = staticMultiSelectField("tags", "[\"a\",\"b\"]");

    var required =
        new WorkRecordField(
            tags.id(),
            tags.tenantId(),
            tags.templateId(),
            tags.templateVersionId(),
            tags.fieldName(),
            tags.fieldCode(),
            tags.fieldType(),
            true,
            tags.defaultValue(),
            tags.optionSource(),
            tags.dictCode(),
            tags.optionsJson(),
            tags.schemaPath(),
            tags.listVisible(),
            tags.filterable(),
            tags.exportable(),
            tags.statistical(),
            tags.sortOrder(),
            tags.enabled(),
            tags.createdAt(),
            tags.updatedAt());

    assertThatThrownBy(
            () -> validator.validate(TENANT_ID, VERSION_ID, List.of(required), "{\"tags\":[]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required field is missing: tags");
  }

  @Test
  void shouldRejectMissingDictCode() {
    var field = selectDictField("priority", null);
    assertThatThrownBy(
            () ->
                validator.validate(TENANT_ID, VERSION_ID, List.of(field), "{\"priority\":\"P1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dictCode is required");
    verify(dictionaryPort, never()).requireEnabledItem(TENANT_ID, null, "P1");
  }

  @Test
  void shouldRejectInvalidJson() {
    assertThatThrownBy(() -> validator.validate(TENANT_ID, VERSION_ID, List.of(), "not-a-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid customDataJson");
  }

  private WorkRecordField withValidation(WorkRecordField field, String validationJson) {
    return new WorkRecordField(
        field.id(),
        field.tenantId(),
        field.templateId(),
        field.templateVersionId(),
        field.fieldName(),
        field.fieldCode(),
        field.fieldType(),
        field.required(),
        field.defaultValue(),
        field.optionSource(),
        field.dictCode(),
        field.optionsJson(),
        field.schemaPath(),
        field.columnSpan(),
        validationJson,
        field.listVisible(),
        field.filterable(),
        field.exportable(),
        field.statistical(),
        field.sortOrder(),
        field.enabled(),
        field.createdAt(),
        field.updatedAt());
  }

  @Test
  void shouldRejectNonObjectRoot() {
    assertThatThrownBy(() -> validator.validate(TENANT_ID, VERSION_ID, List.of(), "[1,2,3]"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be object");
  }
}
