package io.aegisops.workrecord.application.schema;

import java.util.Set;
import java.util.regex.Pattern;

public final class WorkRecordSchemaContract {
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public static final String ROOT_SCHEMA_VERSION_KEY = "x-work-record-schema-version";
  public static final String FIELD_EXTENSION_KEY = "x-work-record";

  public static final String FLAT_FIELD_CODE_KEY = "x-work-record-field-code";
  public static final String FLAT_FIELD_TYPE_KEY = "x-work-record-field-type";
  public static final String FLAT_OPTION_SOURCE_KEY = "x-work-record-option-source";
  public static final String FLAT_DICT_CODE_KEY = "x-work-record-dict-code";
  public static final String FLAT_LIST_VISIBLE_KEY = "x-work-record-list-visible";
  public static final String FLAT_FILTERABLE_KEY = "x-work-record-filterable";
  public static final String FLAT_EXPORTABLE_KEY = "x-work-record-exportable";
  public static final String FLAT_STATISTICAL_KEY = "x-work-record-statistical";

  public static final Pattern FIELD_CODE_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

  public static final Set<String> SUPPORTED_FIELD_TYPES =
      Set.of(
          "text",
          "textarea",
          "number",
          "date",
          "datetime",
          "select",
          "multi_select",
          "user",
          "boolean");

  public static final Set<String> SUPPORTED_OPTION_SOURCES = Set.of("static", "dict");

  public static final Set<String> RESERVED_FIELD_CODES =
      Set.of(
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
          "deleted_at");

  private WorkRecordSchemaContract() {}

  public static boolean isFlatWorkRecordKey(String key) {
    return FLAT_FIELD_CODE_KEY.equals(key)
        || FLAT_FIELD_TYPE_KEY.equals(key)
        || FLAT_OPTION_SOURCE_KEY.equals(key)
        || FLAT_DICT_CODE_KEY.equals(key)
        || FLAT_LIST_VISIBLE_KEY.equals(key)
        || FLAT_FILTERABLE_KEY.equals(key)
        || FLAT_EXPORTABLE_KEY.equals(key)
        || FLAT_STATISTICAL_KEY.equals(key);
  }
}