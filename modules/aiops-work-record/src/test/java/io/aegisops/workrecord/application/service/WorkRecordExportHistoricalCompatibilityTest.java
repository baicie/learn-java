package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.ADMIN_USER_ID;
import static io.aegisops.workrecord.support.WorkRecordFixtures.TEMPLATE_ID;
import static io.aegisops.workrecord.support.WorkRecordFixtures.TENANT_ID;
import static io.aegisops.workrecord.support.WorkRecordFixtures.adminUser;
import static io.aegisops.workrecord.support.WorkRecordFixtures.customColumn;
import static io.aegisops.workrecord.support.WorkRecordFixtures.field;
import static io.aegisops.workrecord.support.WorkRecordFixtures.query;
import static io.aegisops.workrecord.support.WorkRecordFixtures.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordListMeta;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordExportHistoricalCompatibilityTest {

  private WorkRecordRepository repository;
  private WorkRecordQueryService queryService;
  private WorkRecordListMetaService metaService;
  private WorkRecordDictionaryPort dictionaryPort;
  private WorkRecordUserPort userPort;
  private WorkRecordAuditService auditService;
  private WorkRecordFieldIndexRepository fieldRepository;
  private WorkRecordExportService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkRecordRepository.class);
    queryService = mock(WorkRecordQueryService.class);
    metaService = mock(WorkRecordListMetaService.class);
    dictionaryPort = mock(WorkRecordDictionaryPort.class);
    userPort = mock(WorkRecordUserPort.class);
    auditService = mock(WorkRecordAuditService.class);
    fieldRepository = mock(WorkRecordFieldIndexRepository.class);

    service =
        new WorkRecordExportService(
            repository,
            queryService,
            metaService,
            new WorkRecordPermissionService(),
            new WorkRecordExportPolicy(5000),
            new WorkRecordExportColumnResolver(fieldRepository),
            dictionaryPort,
            userPort,
            auditService,
            new WorkRecordCsvWriter(),
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-07-11T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void exportMustResolveDictionaryLabelsByRecordTemplateVersion() {
    UserPrincipal user = adminUser();
    var rawQuery = query(null, List.of());

    var exportColumn = customColumn("priority", "优先级", "priority-current", true);

    RecordListMeta meta =
        new RecordListMeta(
            List.of(),
            List.of(exportColumn),
            List.of(exportColumn),
            List.of(),
            Set.of("priority-v1", "priority-v2"),
            5000,
            List.of("all"));

    var version1Record =
        record("record-v1", "version-1", ADMIN_USER_ID, ADMIN_USER_ID, "{\"priority\":\"P1\"}");

    var version2Record =
        record("record-v2", "version-2", ADMIN_USER_ID, ADMIN_USER_ID, "{\"priority\":\"P2\"}");

    when(queryService.prepareEffectiveQuery(TENANT_ID, rawQuery, user)).thenReturn(rawQuery);

    when(metaService.meta(TENANT_ID, TEMPLATE_ID)).thenReturn(meta);

    when(repository.listForExport(TENANT_ID, rawQuery, 5001))
        .thenReturn(List.of(version1Record, version2Record));

    when(fieldRepository.listByVersions(eq(TENANT_ID), eq(List.of("version-1", "version-2"))))
        .thenReturn(
            List.of(
                field(
                    "version-1",
                    "priority",
                    FieldType.SELECT,
                    OptionSource.DICT,
                    "priority-v1",
                    "[]",
                    false,
                    true,
                    true,
                    true),
                field(
                    "version-2",
                    "priority",
                    FieldType.SELECT,
                    OptionSource.DICT,
                    "priority-v2",
                    "[]",
                    false,
                    true,
                    true,
                    true)));

    when(dictionaryPort.itemLabels(TENANT_ID, "priority-v1")).thenReturn(Map.of("P1", "旧优先级（已禁用）"));

    when(dictionaryPort.itemLabels(TENANT_ID, "priority-v2")).thenReturn(Map.of("P2", "新优先级"));

    when(userPort.displayNames(eq(TENANT_ID), anyCollection())).thenReturn(Map.of());

    var result = service.export(TENANT_ID, rawQuery, List.of("custom.priority"), user);

    String csv = new String(result.content(), StandardCharsets.UTF_8);

    assertThat(result.rowCount()).isEqualTo(2);

    assertThat(csv).contains("旧优先级（已禁用）").contains("新优先级");

    verify(fieldRepository).listByVersions(TENANT_ID, List.of("version-1", "version-2"));
  }
}
