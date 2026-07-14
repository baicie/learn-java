package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.security.DataScope;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordPermissionServiceTest {
  private final WorkRecordPermissionService service = new WorkRecordPermissionService();

  @Test
  void recordAdminCanReadAllRecords() {
    assertThat(
            service.canReadAll(
                principal("admin", Set.of(PermissionCodes.WORK_RECORD_READ_ALL), DataScope.ALL)))
        .isTrue();
  }

  @Test
  void normalUserCanReadOwnedRecord() {
    service.requireRead(
        principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_SELF), DataScope.SELF),
        record("u2", "u1"));
  }

  @Test
  void normalUserCanReadCreatedRecord() {
    service.requireRead(
        principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_SELF), DataScope.SELF),
        record("u1", "u2"));
  }

  @Test
  void normalUserCannotReadOthersRecord() {
    assertThatThrownBy(
            () ->
                service.requireRead(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_SELF), DataScope.SELF),
                    record("u2", "u3")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void writePermissionDoesNotAllowEditingOthersRecord() {
    assertThatThrownBy(
            () ->
                service.requireEdit(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_WRITE), DataScope.SELF),
                    record("u2", "u3")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void allScopeAllowsEditingOthersRecord() {
    service.requireEdit(
        principal("admin", Set.of(PermissionCodes.WORK_RECORD_WRITE), DataScope.ALL),
        record("u2", "u3"));
  }

  @Test
  void readonlyUserCannotEditRecord() {
    assertThatThrownBy(
            () ->
                service.requireEdit(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_SELF), DataScope.SELF),
                    record("u1", "u1")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void exportRequiresExplicitPermission() {
    assertThatThrownBy(
            () ->
                service.requireExport(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_ALL), DataScope.ALL)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void requireQueryAccessAllowsReadAllPrincipal() {
    service.requireQueryAccess(
        principal("admin", Set.of(PermissionCodes.WORK_RECORD_READ_ALL), DataScope.ALL));
  }

  @Test
  void requireQueryAccessAllowsReadSelfPrincipal() {
    service.requireQueryAccess(
        principal("u1", Set.of(PermissionCodes.WORK_RECORD_READ_SELF), DataScope.SELF));
  }

  @Test
  void requireQueryAccessRejectsPrincipalWithNoReadPermissionAndRecordsMetric() {
    CountingTelemetry telemetry = new CountingTelemetry();
    WorkRecordPermissionService instrumented = WorkRecordPermissionService.withTelemetry(telemetry);

    assertThatThrownBy(
            () ->
                instrumented.requireQueryAccess(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_WRITE), DataScope.SELF)))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(telemetry.deniedActions).contains(PermissionCodes.WORK_RECORD_READ_SELF);
  }

  @Test
  void requireQueryAccessRejectsNullPrincipalAndRecordsMetric() {
    CountingTelemetry telemetry = new CountingTelemetry();
    WorkRecordPermissionService instrumented = WorkRecordPermissionService.withTelemetry(telemetry);

    assertThatThrownBy(() -> instrumented.requireQueryAccess(null))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(telemetry.deniedActions).contains(PermissionCodes.WORK_RECORD_READ_SELF);
  }

  @Test
  void requireReadRejectsUserWithoutReadPermissionAndRecordsMetric() {
    CountingTelemetry telemetry = new CountingTelemetry();
    WorkRecordPermissionService instrumented = WorkRecordPermissionService.withTelemetry(telemetry);

    assertThatThrownBy(
            () ->
                instrumented.requireRead(
                    principal("u1", Set.of(PermissionCodes.WORK_RECORD_WRITE), DataScope.SELF),
                    record("u1", "u1")))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(telemetry.deniedActions).contains(PermissionCodes.WORK_RECORD_READ_SELF);
  }

  private UserPrincipal principal(String id, Set<String> permissions, DataScope scope) {
    return new UserPrincipal(
        new UserPrincipal.Identity(id, "t1", id, id),
        Set.of(),
        permissions,
        Map.of("work-record", scope));
  }

  private WorkRecord record(String creatorId, String ownerId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-10T10:00:00+08:00");

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

  private static final class CountingTelemetry implements WorkRecordTelemetry {
    private final java.util.List<String> deniedActions = new java.util.ArrayList<>();

    @Override
    public void recordQuery(String operation, java.time.Duration duration) {}

    @Override
    public void recordExport(String result) {}

    @Override
    public void recordPermissionDenied(String action) {
      deniedActions.add(action);
    }
  }
}
