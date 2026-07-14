package io.aegisops.workrecord.application.service;

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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordPermissionRegressionTest {

  private final WorkRecordPermissionService service =
      new WorkRecordPermissionService(WorkRecordTelemetry.noop());

  @ParameterizedTest
  @MethodSource("readCases")
  void readPermissionMatrix(UserPrincipal principal, boolean expectedAllowed) {
    if (expectedAllowed) {
      service.requireRead(principal, record());
    } else {
      assertThatThrownBy(() -> service.requireRead(principal, record()))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  @ParameterizedTest
  @MethodSource("exportCases")
  void exportPermissionMatrix(UserPrincipal principal, boolean expectedAllowed) {
    if (expectedAllowed) {
      service.requireExport(principal);
    } else {
      assertThatThrownBy(() -> service.requireExport(principal))
          .isInstanceOf(AccessDeniedException.class);
    }
  }

  static Stream<Arguments> readCases() {
    return Stream.of(
        Arguments.of(
            principal(
                "system_admin",
                Set.of(PermissionCodes.WORK_RECORD_READ_ALL),
                DataScope.ALL,
                "admin"),
            true),
        Arguments.of(
            principal(
                "record_admin",
                Set.of(PermissionCodes.WORK_RECORD_READ_ALL),
                DataScope.ALL,
                "record-admin"),
            true),
        Arguments.of(
            principal(
                "normal_user",
                Set.of(PermissionCodes.WORK_RECORD_READ_SELF),
                DataScope.SELF,
                "creator-1"),
            true),
        Arguments.of(
            principal(
                "normal_user",
                Set.of(PermissionCodes.WORK_RECORD_READ_SELF),
                DataScope.SELF,
                "other-user"),
            false),
        Arguments.of(
            principal(
                "readonly_user",
                Set.of(PermissionCodes.WORK_RECORD_READ_SELF),
                DataScope.SELF,
                "creator-1"),
            true));
  }

  static Stream<Arguments> exportCases() {
    return Stream.of(
        Arguments.of(
            principal(
                "system_admin", Set.of(PermissionCodes.WORK_RECORD_EXPORT), DataScope.ALL, "admin"),
            true),
        Arguments.of(
            principal(
                "record_admin",
                Set.of(PermissionCodes.WORK_RECORD_EXPORT),
                DataScope.ALL,
                "record-admin"),
            true),
        Arguments.of(
            principal(
                "normal_user",
                Set.of(PermissionCodes.WORK_RECORD_READ_SELF),
                DataScope.SELF,
                "creator-1"),
            false),
        Arguments.of(
            principal(
                "readonly_user",
                Set.of(PermissionCodes.WORK_RECORD_READ_SELF),
                DataScope.SELF,
                "creator-1"),
            false));
  }

  private static UserPrincipal principal(
      String role, Set<String> permissions, DataScope scope, String userId) {
    return new UserPrincipal(
        new UserPrincipal.Identity(userId, "tenant-1", userId, userId),
        Set.of(role),
        permissions,
        Map.of("work-record", scope));
  }

  private static WorkRecord record() {
    OffsetDateTime time = OffsetDateTime.parse("2026-07-11T10:00:00+08:00");
    return new WorkRecord(
        "record-1",
        "tenant-1",
        "template-1",
        "version-1",
        "日报",
        RecordStatus.DONE,
        "owner-1",
        "creator-1",
        time,
        "{}",
        "{}",
        1,
        time,
        time,
        null);
  }
}
