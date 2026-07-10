package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.ResolvedExportColumn;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkRecordExportColumnResolverTest {
  private WorkRecordFieldIndexRepository fieldRepository;
  private WorkRecordExportColumnResolver resolver;

  @BeforeEach
  void setUp() {
    fieldRepository = mock(WorkRecordFieldIndexRepository.class);
    resolver = new WorkRecordExportColumnResolver(fieldRepository);
  }

  @Test
  void shouldResolveBuiltinColumn() {
    RecordListColumn col = column("title", "标题", "builtin", null, "text", true);
    WorkRecord record = record("r1", "v1");

    List<ResolvedExportColumn> result = resolver.resolve("t1", List.of(col), List.of(record));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).builtin()).isTrue();
    assertThat(result.get(0).key()).isEqualTo("title");
  }

  @Test
  void shouldResolveDynamicColumnForSingleVersion() {
    RecordListColumn col = column("custom.priority", "优先级", "custom", "priority", "select", true);
    WorkRecord record = record("r1", "v1");

    when(fieldRepository.listByVersions("t1", List.of("v1")))
        .thenReturn(
            List.of(field("f1", "t1", "tpl1", "v1", "优先级", "priority", FieldType.SELECT, true)));

    List<ResolvedExportColumn> result = resolver.resolve("t1", List.of(col), List.of(record));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).builtin()).isFalse();
    assertThat(result.get(0).fieldForVersion("v1").fieldCode()).isEqualTo("priority");
  }

  @Test
  void shouldRejectFieldThatIsNotExportableInHistoricalVersion() {
    RecordListColumn col = column("custom.secret", "秘密", "custom", "secret", "text", true);

    WorkRecord r1 = record("r1", "v1");
    WorkRecord r2 = record("r2", "v2");

    when(fieldRepository.listByVersions("t1", List.of("v1", "v2")))
        .thenReturn(
            List.of(
                field("f1", "t1", "tpl1", "v1", "秘密", "secret", FieldType.TEXT, false),
                field("f2", "t1", "tpl1", "v2", "秘密", "secret", FieldType.TEXT, true)));

    assertThatThrownBy(() -> resolver.resolve("t1", List.of(col), List.of(r1, r2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not exportable in template version v1");
  }

  @Test
  void shouldRejectNonExportableColumnFlag() {
    RecordListColumn col = column("custom.secret", "秘密", "custom", "secret", "text", false);

    WorkRecord record = record("r1", "v1");

    assertThatThrownBy(() -> resolver.resolve("t1", List.of(col), List.of(record)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("column is not exportable");
  }

  @Test
  void shouldHandleEmptyRecords() {
    RecordListColumn col = column("custom.priority", "优先级", "custom", "priority", "select", true);

    List<ResolvedExportColumn> result = resolver.resolve("t1", List.of(col), List.of());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).builtin()).isFalse();
  }

  @Test
  void shouldCollectAllVersionIdsFromRecords() {
    RecordListColumn col = column("custom.priority", "优先级", "custom", "priority", "select", true);

    WorkRecord r1 = record("r1", "v1");
    WorkRecord r2 = record("r2", "v2");
    WorkRecord r3 = record("r3", "v3");

    when(fieldRepository.listByVersions(
            eq("t1"),
            argThat(list -> list.size() == 3 && list.containsAll(List.of("v1", "v2", "v3")))))
        .thenReturn(
            List.of(
                field("f1", "t1", "tpl1", "v1", "优先级", "priority", FieldType.SELECT, true),
                field("f2", "t1", "tpl1", "v2", "优先级", "priority", FieldType.SELECT, true),
                field("f3", "t1", "tpl1", "v3", "优先级", "priority", FieldType.SELECT, true)));

    List<ResolvedExportColumn> result = resolver.resolve("t1", List.of(col), List.of(r1, r2, r3));

    assertThat(result).hasSize(1);

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(fieldRepository).listByVersions(eq("t1"), captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder("v1", "v2", "v3");
  }

  @Test
  void shouldRequireAtLeastOneColumn() {
    assertThatThrownBy(() -> resolver.resolve("t1", List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one export column is required");
  }

  private RecordListColumn column(
      String key,
      String title,
      String source,
      String fieldCode,
      String fieldType,
      boolean exportable) {
    return new RecordListColumn(
        key, title, source, fieldCode, fieldType, "static", null, "[]", true, false, exportable, 1);
  }

  private WorkRecordField field(
      String id,
      String tenantId,
      String templateId,
      String templateVersionId,
      String fieldName,
      String fieldCode,
      FieldType fieldType,
      boolean exportable) {
    return new WorkRecordField(
        id,
        tenantId,
        templateId,
        templateVersionId,
        fieldName,
        fieldCode,
        fieldType,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        null,
        true,
        true,
        exportable,
        false,
        1,
        true,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private WorkRecord record(String id, String templateVersionId) {
    return new WorkRecord(
        id,
        "t1",
        "tpl1",
        templateVersionId,
        "Test",
        null,
        "u1",
        "u1",
        OffsetDateTime.now(),
        "{}",
        "{}",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }
}
