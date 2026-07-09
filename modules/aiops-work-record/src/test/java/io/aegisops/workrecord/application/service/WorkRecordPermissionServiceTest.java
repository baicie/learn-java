package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordPermissionServiceTest {
  private final WorkRecordPermissionService service = new WorkRecordPermissionService();

  private UserPrincipal admin() {
    return new UserPrincipal("admin-1", "tenant1", "admin", "", Set.of("admin"));
  }

  private UserPrincipal operator() {
    return new UserPrincipal("operator-1", "tenant1", "operator", "", Set.of("operator"));
  }

  private UserPrincipal stranger() {
    return new UserPrincipal("stranger-1", "tenant1", "stranger", "", Set.of("custom"));
  }

  private UserPrincipal owner() {
    return new UserPrincipal("owner-1", "tenant1", "owner-1", "", Set.of("operator"));
  }

  private UserPrincipal creator() {
    return new UserPrincipal("creator-1", "tenant1", "creator-1", "", Set.of("operator"));
  }

  private WorkRecord record(String ownerId, String creatorId) {
    return new WorkRecord(
        "r1",
        "tenant1",
        "tpl1",
        "v1",
        "title",
        RecordStatus.DRAFT,
        ownerId,
        creatorId,
        OffsetDateTime.now(),
        "{}",
        "{}",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }

  @Test
  void adminShouldReadAll() {
    assertThat(service.canReadAll(admin())).isTrue();
  }

  @Test
  void operatorShouldNotReadAll() {
    assertThat(service.canReadAll(operator())).isFalse();
  }

  @Test
  void operatorShouldReadSelf() {
    assertThat(service.canReadSelf(operator())).isTrue();
  }

  @Test
  void strangerShouldHaveNoSpecialPermission() {
    assertThat(service.canReadAll(stranger())).isFalse();
    assertThat(service.canReadSelf(stranger())).isFalse();
    assertThat(service.canWrite(stranger())).isFalse();
    assertThat(service.canDelete(stranger())).isFalse();
    assertThat(service.canExport(stranger())).isFalse();
  }

  @Test
  void adminCanReadAnyRecord() {
    assertThatCode(() -> service.requireRead(admin(), record("other", "other")))
        .doesNotThrowAnyException();
  }

  @Test
  void operatorCanReadOwnedRecord() {
    assertThatCode(() -> service.requireRead(owner(), record("owner-1", "creator-1")))
        .doesNotThrowAnyException();
  }

  @Test
  void operatorCannotReadUnrelatedRecord() {
    assertThatThrownBy(() -> service.requireRead(operator(), record("other", "other")))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void adminCanWriteAnyRecordWithoutWriteGrantBeingChecked() {
    assertThatCode(() -> service.requireWrite(admin(), record("other", "other")))
        .doesNotThrowAnyException();
  }

  @Test
  void ownerCanWriteOwnRecord() {
    assertThatCode(() -> service.requireWrite(owner(), record("owner-1", "creator-1")))
        .doesNotThrowAnyException();
  }

  @Test
  void creatorCanWriteOwnRecord() {
    assertThatCode(() -> service.requireWrite(creator(), record("owner-1", "creator-1")))
        .doesNotThrowAnyException();
  }

  @Test
  void operatorCannotWriteOthersRecord() {
    assertThatThrownBy(() -> service.requireWrite(operator(), record("other", "other")))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not allowed to update");
  }

  @Test
  void strangerCannotWrite() {
    assertThatThrownBy(() -> service.requireWrite(stranger(), record("other", "other")))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void adminCanDeleteAnyRecord() {
    assertThatCode(() -> service.requireDelete(admin(), record("other", "other")))
        .doesNotThrowAnyException();
  }

  @Test
  void ownerCanDeleteOwnRecord() {
    assertThatCode(() -> service.requireDelete(owner(), record("owner-1", "creator-1")))
        .doesNotThrowAnyException();
  }

  @Test
  void operatorCannotDeleteOthersRecord() {
    // operator has work-record:write but no work-record:delete
    assertThatThrownBy(() -> service.requireDelete(operator(), record("owner-1", "creator-1")))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("not allowed to delete");
  }

  @Test
  void strangerCannotDelete() {
    assertThatThrownBy(() -> service.requireDelete(stranger(), record("other", "other")))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void adminCanExport() {
    assertThatCode(() -> service.requireExport(admin())).doesNotThrowAnyException();
  }

  @Test
  void strangerCannotExport() {
    assertThatThrownBy(() -> service.requireExport(stranger()))
        .isInstanceOf(SecurityException.class);
  }
}
