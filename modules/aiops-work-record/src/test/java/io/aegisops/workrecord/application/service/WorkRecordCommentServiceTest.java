package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.port.WorkRecordCommentRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordComment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordCommentServiceTest {
  @Test
  void validatesMentionTenantAndAuditsCreation() {
    WorkRecordCommentRepository comments = mock(WorkRecordCommentRepository.class);
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    UserService users = mock(UserService.class);
    WorkRecordAuditService audit = mock(WorkRecordAuditService.class);
    WorkRecord record = mock(WorkRecord.class);
    when(record.templateId()).thenReturn("template-1");
    when(records.get(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("record-1"),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(record);
    UserAccount mentioned = mock(UserAccount.class);
    when(mentioned.tenantId()).thenReturn("tenant-1");
    when(users.getById("user-2")).thenReturn(mentioned);
    WorkRecordComment created =
        new WorkRecordComment(
            "comment-1",
            "record-1",
            "hello",
            List.of("user-2"),
            "user-1",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            1);
    when(comments.create("tenant-1", "record-1", "hello", List.of("user-2"), "user-1"))
        .thenReturn(created);
    var service = new WorkRecordCommentService(comments, records, users, audit);

    assertThat(
            service.create(
                "tenant-1",
                "record-1",
                new WorkRecordCommentService.CommentCommand(" hello ", List.of("user-2")),
                principal()))
        .isSameAs(created);
    verify(audit)
        .record(
            "tenant-1",
            "record-1",
            "template-1",
            "work_record_comment",
            "comment-1",
            "work_record.comment.create",
            "user-1",
            "{\"recordId\":\"record-1\"}");
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:comment", "work-record:read:self"),
        Map.of());
  }
}
