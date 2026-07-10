package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.RecordListMeta;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordExportServiceTest {
  private final WorkRecordRepository repository =
      mock(WorkRecordRepository.class);
  private final WorkRecordQueryService queryService =
      mock(WorkRecordQueryService.class);
  private final WorkRecordListMetaService metaService =
      mock(WorkRecordListMetaService.class);
  private final WorkRecordPermissionService permissionService =
      mock(WorkRecordPermissionService.class);
  private final WorkRecordDictionaryPort dictionaryPort =
      mock(WorkRecordDictionaryPort.class);
  private final WorkRecordUserPort userPort =
      mock(WorkRecordUserPort.class);
  private final WorkRecordAuditService auditService =
      mock(WorkRecordAuditService.class);

  private final WorkRecordExportPolicy exportPolicy =
      new WorkRecordExportPolicy(5000);

  private final Clock clock =
      Clock.fixed(
          Instant.parse("2026-07-10T07:30:00Z"),
          ZoneId.of("Asia/Shanghai"));

  private final WorkRecordExportService service =
      new WorkRecordExportService(
          repository,
          queryService,
          metaService,
          permissionService,
          exportPolicy,
          dictionaryPort,
          userPort,
          auditService,
          new WorkRecordCsvWriter(),
          new ObjectMapper(),
          clock);

  @Test
  void shouldExportCurrentColumnsWithLabelsAndAudit() {
    RecordQuery query = query();

    when(
            queryService.prepareEffectiveQuery(
                "t1", query, user()))
        .thenReturn(query);

    when(metaService.meta("t1", "tpl1"))
        .thenReturn(
            new RecordListMeta(
                List.of(),
                List.of(
                    column("title", "标题", "builtin", null, "text", null, null, true),
                    column("ownerId", "负责人", "builtin", null, "user", null, null, true),
                    column("custom.priority", "优先级", "custom", "priority", "select", "dict", "priority_dict", true)),
                List.of(),
                Set.of("priority_dict"),
                5000,
                List.of("all")));

    when(
            repository.listForExport(
                eq("t1"), eq(query), eq(5001)))
        .thenReturn(List.of(record()));

    when(
            dictionaryPort.itemLabels(
                "t1", "priority_dict"))
        .thenReturn(
            Map.of("P1", "高"));

    when(
            userPort.displayNames(
                eq("t1"), anyCollection()))
        .thenReturn(
            Map.of("u1", "张三"));

    var result =
        service.export(
            "t1",
            query,
            List.of("title", "ownerId", "custom.priority"),
            user());

    String csv =
        new String(
            result.content(),
            StandardCharsets.UTF_8);

    assertThat(result.rowCount()).isEqualTo(1);
    assertThat(result.fileName())
        .isEqualTo(
            "work-records-20260710-153000.csv");

    assertThat(csv)
        .contains("\"标题\",\"负责人\",\"优先级\"")
        .contains("\"日报\",\"张三\",\"高\"");

    verify(auditService)
        .record(
            eq("t1"),
            isNull(),
            eq("tpl1"),
            eq("work_record_export"),
            anyString(),
            eq("work_record.record.export"),
            eq("u1"),
            contains("\"rowCount\":1"));
  }

  @Test
  void shouldRejectNonExportableColumn() {
    RecordQuery query = query();

    when(
            queryService.prepareEffectiveQuery(
                "t1", query, user()))
        .thenReturn(query);

    when(metaService.meta("t1", "tpl1"))
        .thenReturn(
            new RecordListMeta(
                List.of(),
                List.of(
                    column("custom.secret", "秘密", "custom", "secret", "text", "static", null, false)),
                List.of(),
                Set.of(),
                5000,
                List.of()));

    assertThatThrownBy(
            () ->
                service.export(
                    "t1",
                    query,
                    List.of("custom.secret"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "column is not exportable");

    verifyNoInteractions(repository);
  }

  @Test
  void shouldRejectResultOverLimitAndWriteAudit() {
    WorkRecordExportPolicy oneRowPolicy =
        new WorkRecordExportPolicy(1);

    WorkRecordExportService limitedService =
        new WorkRecordExportService(
            repository,
            queryService,
            metaService,
            permissionService,
            oneRowPolicy,
            dictionaryPort,
            userPort,
            auditService,
            new WorkRecordCsvWriter(),
            new ObjectMapper(),
            clock);

    RecordQuery query = query();

    when(
            queryService.prepareEffectiveQuery(
                "t1", query, user()))
        .thenReturn(query);

    when(metaService.meta("t1", "tpl1"))
        .thenReturn(
            new RecordListMeta(
                List.of(),
                List.of(
                    column("title", "标题", "builtin", null, "text", null, null, true)),
                List.of(),
                Set.of(),
                1,
                List.of()));

    when(
            repository.listForExport(
                "t1", query, 2))
        .thenReturn(
            List.of(record(), record()));

    assertThatThrownBy(
            () ->
                limitedService.export(
                    "t1",
                    query,
                    List.of("title"),
                    user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("超过 1 行");

    verify(auditService)
        .record(
            eq("t1"),
            isNull(),
            eq("tpl1"),
            eq("work_record_export"),
            anyString(),
            eq("work_record.record.export_rejected"),
            eq("u1"),
            contains("limit_exceeded"));
  }

  @Test
  void shouldRequireExportPermission() {
    doThrow(
            new SecurityException(
                "not allowed to export work records"))
        .when(permissionService)
        .requireExport(user());

    assertThatThrownBy(
            () ->
                service.export(
                    "t1",
                    query(),
                    List.of("title"),
                    user()))
        .isInstanceOf(SecurityException.class);

    verifyNoInteractions(
        queryService,
        repository,
        auditService);
  }

  private RecordListColumn column(
      String key,
      String title,
      String source,
      String fieldCode,
      String fieldType,
      String optionSource,
      String dictCode,
      boolean exportable) {
    return new RecordListColumn(
        key,
        title,
        source,
        fieldCode,
        fieldType,
        optionSource,
        dictCode,
        "[]",
        true,
        false,
        exportable,
        1);
  }

  private RecordQuery query() {
    return new RecordQuery(
        1,
        20,
        "tpl1",
        null,
        List.of("done"),
        null,
        null,
        null,
        null,
        null,
        false,
        "u1",
        List.of(),
        "recordTime",
        "desc",
        "all",
        null);
  }

  private WorkRecord record() {
    OffsetDateTime now =
        OffsetDateTime.parse(
            "2026-07-10T15:00:00+08:00");

    return new WorkRecord(
        "r1",
        "t1",
        "tpl1",
        "v1",
        "日报",
        RecordStatus.DONE,
        "u1",
        "u1",
        now,
        "{}",
        "{\"priority\":\"P1\"}",
        1,
        now,
        now,
        null);
  }

  private UserPrincipal user() {
    return new UserPrincipal(
        "u1",
        "t1",
        "alice",
        "张三",
        Set.of("admin"));
  }
}
