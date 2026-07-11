package io.aegisops.workrecord.support;

import io.aegisops.security.DataScope;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一测试 Fixture：稳定租户与基础身份，便于在不同时期添加的测试之间保持一致的最小可复现场景。
 *
 * <p>测试方法应描述"长期不变的业务契约"（例如 normalUserCannotReadOthersRecord），而不是把阶段编号
 * 作为名字的一部分。
 */
public final class WorkRecordFixtures {

  public static final String TENANT_ID = "tenant-1";
  public static final String OTHER_TENANT_ID = "tenant-2";
  public static final String TEMPLATE_ID = "template-1";
  public static final String VERSION_ID = "version-1";
  public static final String ADMIN_USER_ID = "user-admin";
  public static final String NORMAL_USER_ID = "user-normal";
  public static final String READONLY_USER_ID = "user-readonly";

  public static final OffsetDateTime NOW =
      OffsetDateTime.parse("2026-07-11T10:00:00+08:00");

  private WorkRecordFixtures() {}

  public static WorkRecordTemplate template(String currentVersionId) {
    TemplateStatus status =
        currentVersionId == null ? TemplateStatus.DRAFT : TemplateStatus.PUBLISHED;

    return new WorkRecordTemplate(
        TEMPLATE_ID,
        TENANT_ID,
        "daily",
        "日报",
        "日报模板",
        status,
        true,
        currentVersionId,
        "{}",
        "{}",
        ADMIN_USER_ID,
        NOW,
        NOW,
        null);
  }

  public static WorkRecordTemplateVersion version(String versionId) {
    return new WorkRecordTemplateVersion(
        versionId,
        TENANT_ID,
        TEMPLATE_ID,
        1,
        "v1",
        "{}",
        "{}",
        "[]",
        ADMIN_USER_ID,
        NOW,
        NOW);
  }

  public static WorkRecordField field(
      String versionId,
      String fieldCode,
      FieldType fieldType,
      OptionSource optionSource,
      String dictCode,
      String optionsJson,
      boolean required,
      boolean enabled,
      boolean filterable,
      boolean exportable) {

    return new WorkRecordField(
        "field-" + versionId + "-" + fieldCode,
        TENANT_ID,
        TEMPLATE_ID,
        versionId,
        fieldCode,
        fieldCode,
        fieldType,
        required,
        null,
        optionSource,
        dictCode,
        optionsJson,
        ".properties." + fieldCode,
        true,
        filterable,
        exportable,
        false,
        0,
        enabled,
        NOW,
        NOW);
  }

  public static WorkRecordField textField(String versionId, String fieldCode) {
    return field(
        versionId,
        fieldCode,
        FieldType.TEXT,
        OptionSource.STATIC,
        null,
        "[]",
        false,
        true,
        true,
        true);
  }

  public static WorkRecord record(
      String id,
      String versionId,
      String creatorId,
      String ownerId,
      String customDataJson) {
    return new WorkRecord(
        id,
        TENANT_ID,
        TEMPLATE_ID,
        versionId,
        "日报",
        RecordStatus.DONE,
        ownerId,
        creatorId,
        NOW,
        "{}",
        customDataJson,
        1,
        NOW,
        NOW,
        null);
  }

  public static UserPrincipal adminUser() {
    Set<String> permissions =
        Set.of(
            PermissionCodes.WORK_RECORD_READ_ALL,
            PermissionCodes.WORK_RECORD_READ_SELF,
            PermissionCodes.WORK_RECORD_WRITE,
            PermissionCodes.WORK_RECORD_DELETE,
            PermissionCodes.WORK_RECORD_EXPORT,
            PermissionCodes.WORK_RECORD_TEMPLATE_READ,
            PermissionCodes.WORK_RECORD_TEMPLATE_WRITE);

    Map<String, DataScope> dataScopes =
        Map.of(
            "work-record",
            DataScope.ALL,
            "work-record-template",
            DataScope.ALL);

    return new UserPrincipal(
        ADMIN_USER_ID,
        TENANT_ID,
        "admin",
        "管理员",
        Set.of("record-admin"),
        permissions,
        dataScopes);
  }

  public static UserPrincipal normalUser() {
    Set<String> permissions =
        Set.of(
            PermissionCodes.WORK_RECORD_READ_SELF,
            PermissionCodes.WORK_RECORD_WRITE);

    Map<String, DataScope> dataScopes =
        Map.of(
            "work-record",
            DataScope.SELF,
            "work-record-template",
            DataScope.SELF);

    return new UserPrincipal(
        NORMAL_USER_ID,
        TENANT_ID,
        "normal",
        "普通用户",
        Set.of("normal-user"),
        permissions,
        dataScopes);
  }

  public static UserPrincipal readonlyUser() {
    Set<String> permissions = Set.of(PermissionCodes.WORK_RECORD_READ_SELF);

    Map<String, DataScope> dataScopes = Map.of("work-record", DataScope.SELF);

    return new UserPrincipal(
        READONLY_USER_ID,
        TENANT_ID,
        "readonly",
        "只读用户",
        Set.of("readonly-user"),
        permissions,
        dataScopes);
  }

  public static RecordQuery query(String templateVersionId, List<RecordDynamicFilter> filters) {
    return new RecordQuery(
        1,
        20,
        TEMPLATE_ID,
        templateVersionId,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        filters,
        "recordTime",
        "desc",
        "all",
        null);
  }

  public static RecordListColumn customColumn(
      String fieldCode, String title, String dictCode, boolean exportable) {

    return new RecordListColumn(
        "custom." + fieldCode,
        title,
        "custom",
        fieldCode,
        "select",
        "dict",
        dictCode,
        "[]",
        true,
        false,
        exportable,
        0);
  }
}
