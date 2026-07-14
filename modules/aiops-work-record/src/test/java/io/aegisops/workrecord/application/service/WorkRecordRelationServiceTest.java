package io.aegisops.workrecord.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.RelationTargetPort;
import io.aegisops.workrecord.application.port.WorkRecordRelationRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordRelationServiceTest {
  @Test
  void deleteAuditsSuccessfulMutation() {
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    WorkRecordPermissionService permissions = mock(WorkRecordPermissionService.class);
    WorkRecordRelationRepository relations = mock(WorkRecordRelationRepository.class);
    WorkRecordAuditService audit = mock(WorkRecordAuditService.class);
    WorkRecord record = mock(WorkRecord.class);
    UserPrincipal principal = principal();
    when(record.templateId()).thenReturn("template-1");
    when(records.get("tenant-1", "record-1", principal)).thenReturn(record);
    when(relations.delete("tenant-1", "record-1", "relation-1")).thenReturn(true);
    var service =
        new WorkRecordRelationService(
            records, permissions, mock(RelationTargetPort.class), relations, audit);

    service.delete("tenant-1", "record-1", "relation-1", principal);

    verify(audit)
        .record(
            "tenant-1",
            "record-1",
            "template-1",
            "work_record_relation",
            "relation-1",
            "work_record.relation.delete",
            "user-1",
            "{\"recordId\":\"record-1\"}");
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:relation", "work-record:edit:self", "work-record:read:self"),
        Map.of());
  }
}
