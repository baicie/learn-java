package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.InMemoryDistributedLeaseService;
import io.aegisops.security.InMemoryTenantRateLimiter;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordListColumn;
import io.aegisops.workrecord.application.command.RecordListMeta;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordExportServiceRecordIdsTest {
  private WorkRecordRepository repository;
  private WorkRecordQueryService queryService;
  private WorkRecordListMetaService metaService;
  private WorkRecordAuditService auditService;
  private WorkRecordFieldIndexRepository fieldRepository;
  private WorkRecordUserPort userPort;
  private WorkRecordExportService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkRecordRepository.class);
    queryService = mock(WorkRecordQueryService.class);
    metaService = mock(WorkRecordListMetaService.class);
    WorkRecordPermissionService permissionService = mock(WorkRecordPermissionService.class);
    WorkRecordDictionaryPort dictionaryPort = mock(WorkRecordDictionaryPort.class);
    userPort = mock(WorkRecordUserPort.class);
    auditService = mock(WorkRecordAuditService.class);
    WorkRecordExportPolicy exportPolicy = new WorkRecordExportPolicy(5000);
    fieldRepository = mock(WorkRecordFieldIndexRepository.class);
    FieldPolicyService fieldPolicies = mock(FieldPolicyService.class);
    when(fieldPolicies.canReadField(anyString(), anyString(), anyString(), any())).thenReturn(true);
    WorkRecordExportColumnResolver columnResolver =
        new WorkRecordExportColumnResolver(fieldRepository, fieldPolicies);
    Clock clock = Clock.fixed(Instant.parse("2026-07-10T07:30:00Z"), ZoneId.of("Asia/Shanghai"));

    service =
        new WorkRecordExportService(
            repository,
            queryService,
            metaService,
            permissionService,
            exportPolicy,
            columnResolver,
            dictionaryPort,
            userPort,
            auditService,
            new WorkRecordCsvWriter(),
            new WorkRecordExportGuard(
                new InMemoryTenantRateLimiter(),
                new InMemoryDistributedLeaseService(),
                new WorkRecordProductionProperties(),
                WorkRecordTelemetry.noop()),
            WorkRecordTelemetry.noop(),
            new ObjectMapper(),
            clock);
  }

  @Test
  void shouldRejectEmptyRecordIds() {
    RecordQuery query = queryWithRecordIds(List.of());

    assertThatThrownBy(() -> service.export("t1", query, List.of("title"), user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未选择要导出的记录");

    verifyNoInteractions(queryService, repository, auditService);
  }

  @Test
  void shouldRejectRecordIdsExceedingMaxRows() {
    List<String> ids =
        java.util.stream.IntStream.range(0, 5001).mapToObj(index -> "r" + index).toList();
    RecordQuery query = queryWithRecordIds(ids);

    assertThatThrownBy(() -> service.export("t1", query, List.of("title"), user()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("超过 5000 行");

    verifyNoInteractions(queryService, repository, auditService);
  }

  @Test
  void shouldExportSelectedRecordIdsWithAuditSnapshot() {
    RecordQuery query = queryWithRecordIds(List.of("r1"));

    when(queryService.prepareEffectiveQuery("t1", query, user())).thenReturn(query);
    when(metaService.meta("t1", "tpl1"))
        .thenReturn(
            new RecordListMeta(
                List.of(),
                List.of(column()),
                List.of(column()),
                List.of(),
                Set.of(),
                5000,
                List.of("all")));
    when(repository.listForExport("t1", query, 5001)).thenReturn(List.of(record()));
    when(fieldRepository.listByVersions("t1", List.of("v1"))).thenReturn(List.of());
    when(userPort.displayNames(eq("t1"), anyCollection())).thenReturn(Map.of());

    var result = service.export("t1", query, List.of("title"), user());

    assertThat(result.rowCount()).isEqualTo(1);
    verify(repository).listForExport("t1", query, 5001);
    verify(auditService)
        .record(
            eq("t1"),
            isNull(),
            eq("tpl1"),
            eq("work_record_export"),
            anyString(),
            eq("work_record.record.export"),
            eq("u1"),
            contains("\"recordIds\":[\"r1\"]"));
  }

  private RecordListColumn column() {
    return new RecordListColumn(
        "title", "标题", "builtin", null, "text", null, null, "[]", true, false, true, 1);
  }

  private RecordQuery queryWithRecordIds(List<String> recordIds) {
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
        null,
        recordIds);
  }

  private WorkRecord record() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-10T15:00:00+08:00");
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

  private UserPrincipal cachedUser;

  @BeforeEach
  void setUpUser() {
    cachedUser =
        new UserPrincipal(
            new UserPrincipal.Identity("u1", "t1", "alice", "张三"),
            Set.of("admin"),
            Set.of("work-record:export", "work-record:read:self"),
            java.util.Map.of());
  }

  private UserPrincipal user() {
    return cachedUser;
  }
}
