package io.aegisops.workrecord.domain.rule;

import java.util.Set;
import java.util.regex.Pattern;

public final class FieldCodeRules {
  private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

  private static final Set<String> RESERVED =
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
          "created_at",
          "updated_at",
          "deleted_at");

  private FieldCodeRules() {}

  public static void validate(String fieldCode) {
    if (fieldCode == null || fieldCode.isBlank()) {
      throw new IllegalArgumentException("fieldCode is required");
    }
    if (!PATTERN.matcher(fieldCode).matches()) {
      throw new IllegalArgumentException("invalid fieldCode: " + fieldCode);
    }
    if (RESERVED.contains(fieldCode)) {
      throw new IllegalArgumentException("reserved fieldCode: " + fieldCode);
    }
  }
}
