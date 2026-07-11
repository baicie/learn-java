package io.aegisops.workrecord.application.service;

import static io.aegisops.workrecord.support.WorkRecordFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * 导出服务授权与上下界契约。
 *
 * <p>覆盖：导出权限、上限检查与历史模板版本字段隔离。
 */
class WorkRecordExportHistoricalCompatibilityTest {

  private WorkRecordRepository repository;
  private WorkRecordQueryService queryService;
  private WorkRecordListMetaService metaService;
  private WorkRecordPermissionService permissionService;
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
    permissionService = new WorkRecordPermissionService();
    dictionaryPort = mock(WorkRecordDictionaryPort.class);
    userPort = mock(WorkRecordUserPort.class);
    auditService = mock(WorkRecordAuditService.class);
    fieldRepository = mock(WorkRecordFieldIndexRepository.class);

    service =
        new WorkRecordExportService(
            repository,
            queryService,
            metaService,
            permissionService,
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
  void userWithoutExportPermissionMustBeRejected() {
    assertThatThrownBy(
            () ->
                service.export(
                    TENANT_ID,
                    query(null, List.of()),
                    List.of("title"),
                    readonlyUser()))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(repository);
    verifyNoInteractions(auditService);
  }

  @Test
  void requestedColumnsMustIncludeAllRecordVersionsInOneResolverCall() {
    var query = query(null, List.of());

    when(queryService.prepareEffectiveQuery(eq(TENANT_ID), eq(query), eq(adminUser())))
        .thenReturn(query);
    when(repository.listForExport(eq(TENANT_ID), eq(query), anyInt()))
        .thenReturn(
            List.of(
                record("record-v1", "version-1", ADMIN_USER_ID, ADMIN_USER_ID, "{\"priority\":\"P1\"}"),
                record("record-v2", "version-2", ADMIN_USER_ID, ADMIN_USER_ID, "{\"priority\":\"P2\"}")));
    when(
            fieldRepository.listByVersions(
                eq(TENANT_ID),
                argThat(versions -> versions.containsAll(List.of("version-1", "version-2")))))
        .thenReturn(List.of());

    try {
      service.export(
          TENANT_ID,
          query,
          List.of("custom.priority"),
          adminUser());
    } catch (RuntimeException expected) {
      // 期望 column resolve 抛错，因为 columns meta 为空。
      // 真正关心的是字段解析调用被触达。
    }

    verify(fieldRepository)
        .listByVersions(
            eq(TENANT_ID),
            argThat(
                versions ->
                    versions.containsAll(List.of("version-1", "version-2"))
                        && versions.size() == 2));
  }
}