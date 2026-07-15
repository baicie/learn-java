package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ConflictException;
import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.port.WorkRecordCommentRepository;
import io.aegisops.workrecord.domain.model.WorkRecordComment;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkRecordCommentService {
  private final WorkRecordCommentRepository comments;
  private final WorkRecordQueryService records;
  private final UserService users;
  private final WorkRecordAuditService audit;

  public WorkRecordCommentService(
      WorkRecordCommentRepository comments,
      WorkRecordQueryService records,
      UserService users,
      WorkRecordAuditService audit) {
    this.comments = comments;
    this.records = records;
    this.users = users;
    this.audit = audit;
  }

  public List<WorkRecordComment> list(String tenantId, String recordId, UserPrincipal principal) {
    records.get(tenantId, recordId, principal);
    return comments.list(tenantId, recordId, 200);
  }

  @Transactional
  public WorkRecordComment create(
      String tenantId, String recordId, CommentCommand command, UserPrincipal principal) {
    requireComment(principal, tenantId);
    if (command == null) {
      throw new IllegalArgumentException("comment command is required");
    }
    var record = records.get(tenantId, recordId, principal);
    String content = normalizeContent(command.content());
    List<String> mentions = normalizeMentions(tenantId, command.mentionUserIds());
    WorkRecordComment comment =
        comments.create(tenantId, recordId, content, mentions, principal.id());
    audit.record(
        tenantId,
        recordId,
        record.templateId(),
        "work_record_comment",
        comment.id(),
        "work_record.comment.create",
        principal.id(),
        "{\"recordId\":\"" + recordId + "\"}");
    return comment;
  }

  @Transactional
  public WorkRecordComment update(
      String tenantId,
      String recordId,
      String commentId,
      CommentCommand command,
      int expectedVersion,
      UserPrincipal principal) {
    requireComment(principal, tenantId);
    if (command == null) {
      throw new IllegalArgumentException("comment command is required");
    }
    records.get(tenantId, recordId, principal);
    WorkRecordComment existing = requireOwned(tenantId, recordId, commentId, principal);
    if (!comments.update(
        tenantId,
        existing.id(),
        normalizeContent(command.content()),
        normalizeMentions(tenantId, command.mentionUserIds()),
        expectedVersion)) {
      throw new ConflictException("comment was modified; refresh and retry");
    }
    return comments.find(tenantId, commentId).orElseThrow();
  }

  @Transactional
  public void delete(
      String tenantId,
      String recordId,
      String commentId,
      int expectedVersion,
      UserPrincipal principal) {
    requireComment(principal, tenantId);
    records.get(tenantId, recordId, principal);
    requireOwned(tenantId, recordId, commentId, principal);
    if (!comments.softDelete(tenantId, commentId, expectedVersion)) {
      throw new ConflictException("comment was modified; refresh and retry");
    }
  }

  private WorkRecordComment requireOwned(
      String tenantId, String recordId, String commentId, UserPrincipal principal) {
    WorkRecordComment comment =
        comments
            .find(tenantId, commentId)
            .filter(value -> recordId.equals(value.recordId()))
            .orElseThrow(() -> new ResourceNotFoundException("comment not found: " + commentId));
    if (!principal.id().equals(comment.createdBy())
        && !principal.hasPermission(PermissionCodes.WORK_RECORD_COMMENT_MODERATE)) {
      throw new AccessDeniedException("not allowed to modify this comment");
    }
    return comment;
  }

  private List<String> normalizeMentions(String tenantId, List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    if (values.size() > 20) {
      throw new IllegalArgumentException("at most 20 mentions are allowed");
    }
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String userId : values) {
      if (userId == null || userId.isBlank() || !result.add(userId)) {
        continue;
      }
      if (!tenantId.equals(users.getById(userId).tenantId())) {
        throw new IllegalArgumentException("mentioned user is outside tenant");
      }
    }
    return List.copyOf(result);
  }

  private static String normalizeContent(String value) {
    String content = value == null ? "" : value.trim();
    if (content.isEmpty() || content.length() > 4000) {
      throw new IllegalArgumentException("comment length must be between 1 and 4000");
    }
    return content;
  }

  private static void requireComment(UserPrincipal principal, String tenantId) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_COMMENT)) {
      throw new AccessDeniedException("not allowed to comment");
    }
  }

  public record CommentCommand(String content, List<String> mentionUserIds) {
    public CommentCommand {
      mentionUserIds = mentionUserIds == null ? List.of() : List.copyOf(mentionUserIds);
    }
  }
}
