package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.security.DataScope;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordPermissionServiceTest {
  private final WorkRecordPermissionService service =
      new WorkRecordPermissionService();

  @Test
  void recordAdminCanReadAllRecords() {
    assertThat(
            service.canReadAll(
                principal(
                    "admin",
                    Set.of(
                        PermissionCodes
                            .WORK_RECORD_READ_ALL),
                    DataScope.ALL)))
        .isTrue();
  }

  @Test
  void normalUserCanReadOwnedRecord() {
    service.requireRead(
        principal(
            "u1",
            Set.of(
                PermissionCodes
                    .WORK_RECORD_READ_SELF),
            DataScope.SELF),
        record("u2", "u1"));
  }

  @Test
  void normalUserCanReadCreatedRecord() {
    service.requireRead(
        principal(
            "u1",
            Set.of(
                PermissionCodes
                    .WORK_RECORD_READ_SELF),
            DataScope.SELF),
        record("u1", "u2"));
  }

  @Test
  void normalUserCannotReadOthersRecord() {
    assertThatThrownBy(
            () ->
                service.requireRead(
                    principal(
                        "u1",
                        Set.of(
                            PermissionCodes
                                .WORK_RECORD_READ_SELF),
                        DataScope.SELF),
                    record("u2", "u3")))
        .isInstanceOf(
            SecurityException.class);
  }

  @Test
  void writePermissionDoesNotAllowEditingOthersRecord() {
    assertThatThrownBy(
            () ->
                service.requireEdit(
                    principal(
                        "u1",
                        Set.of(
                            PermissionCodes
                                .WORK_RECORD_WRITE),
                        DataScope.SELF),
                    record("u2", "u3")))
        .isInstanceOf(
            SecurityException.class);
  }

  @Test
  void allScopeAllowsEditingOthersRecord() {
    service.requireEdit(
        principal(
            "admin",
            Set.of(
                PermissionCodes
                    .WORK_RECORD_WRITE),
            DataScope.ALL),
        record("u2", "u3"));
  }

  @Test
  void readonlyUserCannotEditRecord() {
    assertThatThrownBy(
            () ->
                service.requireEdit(
                    principal(
                        "u1",
                        Set.of(
                            PermissionCodes
                                .WORK_RECORD_READ_SELF),
                        DataScope.SELF),
                    record("u1", "u1")))
        .isInstanceOf(
            SecurityException.class);
  }

  @Test
  void exportRequiresExplicitPermission() {
    assertThatThrownBy(
            () ->
                service.requireExport(
                    principal(
                        "u1",
                        Set.of(
                            PermissionCodes
                                .WORK_RECORD_READ_ALL),
                        DataScope.ALL)))
        .isInstanceOf(
            SecurityException.class);
  }

  private UserPrincipal principal(
      String id,
      Set<String> permissions,
      DataScope scope) {
    return new UserPrincipal(
        id,
        "t1",
        id,
        id,
        Set.of(),
        permissions,
        Map.of(
            "work-record",
            scope));
  }

  private WorkRecord record(
      String creatorId,
      String ownerId) {
    OffsetDateTime now =
        OffsetDateTime.parse(
            "2026-07-10T10:00:00+08:00");

    return new WorkRecord(
        "r1",
        "t1",
        "tpl1",
        "v1",
        "日报",
        RecordStatus.DRAFT,
        ownerId,
        creatorId,
        now,
        "{}",
        "{}",
        1,
        now,
        now,
        null);
  }
}
