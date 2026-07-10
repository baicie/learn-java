package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class WorkRecordAuditSnapshotsTest {

  private final WorkRecordAuditSnapshots snapshots =
      new WorkRecordAuditSnapshots(new ObjectMapper());

  @Test
  void recordShouldContainAllImmutableFields() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecord record =
        new WorkRecord(
            "r1",
            "t1",
            "tpl1",
            "v1",
            "日报",
            RecordStatus.DRAFT,
            "u1",
            "u1",
            now,
            "{}",
            "{\"k\":\"v\"}",
            1,
            now,
            now,
            null);

    var map = snapshots.record(record);

    assertThat(map.get("id")).isEqualTo("r1");
    assertThat(map.get("title")).isEqualTo("日报");
    assertThat(map.get("status")).isEqualTo("draft");
    assertThat(map.get("templateId")).isEqualTo("tpl1");
    assertThat(map.get("templateVersionId")).isEqualTo("v1");
    assertThat(map.get("ownerId")).isEqualTo("u1");
    assertThat(map.get("creatorId")).isEqualTo("u1");
    assertThat(map.get("rowVersion")).isEqualTo(1);
    assertThat(map.get("customData")).isNotNull();
  }

  @Test
  void recordTombstoneShouldMarkAsDeleted() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecord record =
        new WorkRecord(
            "r1",
            "t1",
            "tpl1",
            "v1",
            "日报",
            RecordStatus.DRAFT,
            "u1",
            "u1",
            now,
            "{}",
            "{}",
            1,
            now,
            now,
            null);

    var map = snapshots.recordTombstone(record, now);

    assertThat(map.get("deleted")).isEqualTo(true);
    assertThat(map.get("deletedAt")).isEqualTo(now);
  }

  @Test
  void templateShouldCaptureDraftJson() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecordTemplate template =
        new WorkRecordTemplate(
            "tpl1",
            "t1",
            "daily",
            "日报",
            "desc",
            TemplateStatus.DRAFT,
            true,
            null,
            "{\"k\":1}",
            "{\"d\":1}",
            "u1",
            now,
            now,
            null);

    var map = snapshots.template(template);

    assertThat(map.get("id")).isEqualTo("tpl1");
    assertThat(map.get("code")).isEqualTo("daily");
    assertThat(map.get("name")).isEqualTo("日报");
    assertThat(map.get("draftSchema")).isNotNull();
    assertThat(map.get("draftDesigner")).isNotNull();
  }

  @Test
  void versionShouldIncludeVersionNo() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1", "t1", "tpl1", 1, "v1", "{}", "{}", "[]", "u1", now, now);

    var map = snapshots.version(version);

    assertThat(map.get("versionNo")).isEqualTo(1);
    assertThat(map.get("templateId")).isEqualTo("tpl1");
  }

  @Test
  void fieldShouldCaptureSemanticAndVersion() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecordField field =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v1",
            "优先级",
            "priority",
            FieldType.SELECT,
            false,
            null,
            OptionSource.DICT,
            "priority_dict",
            "[]",
            ".properties.priority",
            true,
            true,
            true,
            false,
            0,
            true,
            now,
            now);

    var map = snapshots.field(field);

    assertThat(map.get("fieldCode")).isEqualTo("priority");
    assertThat(map.get("fieldName")).isEqualTo("优先级");
    assertThat(map.get("fieldType")).isEqualTo("select");
    assertThat(map.get("optionSource")).isEqualTo("dict");
    assertThat(map.get("dictCode")).isEqualTo("priority_dict");
    assertThat(map.get("listVisible")).isEqualTo(true);
    assertThat(map.get("enabled")).isEqualTo(true);
  }

  @Test
  void fieldSemanticShouldExcludeIdentityFields() {
    OffsetDateTime now = OffsetDateTime.now();
    WorkRecordField field =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v1",
            "标题",
            "title",
            FieldType.TEXT,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            null,
            true,
            false,
            true,
            false,
            0,
            true,
            now,
            now);

    var map = snapshots.fieldSemantic(field);

    // semantic snapshot must not include id/timestamps, only editable fields
    assertThat(map).doesNotContainKey("id");
    assertThat(map).doesNotContainKey("createdAt");
    assertThat(map).doesNotContainKey("updatedAt");
    assertThat(map).containsKey("fieldCode");
    assertThat(map).containsKey("fieldName");
  }
}
