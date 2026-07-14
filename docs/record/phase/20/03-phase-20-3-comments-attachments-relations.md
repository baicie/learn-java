---
title: Phase 20.3 评论时间线、附件与关联对象
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.3：评论时间线、附件与关联对象

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

## 数据库迁移

### 4.2 V0032：评论、附件、关联对象

```sql
-- V0032__init_phase20_collaboration.sql

create table if not exists work_record.wr_comment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    content text not null,
    mentions_json jsonb not null default '[]'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    row_version integer not null default 1,

    constraint ck_wr_comment_content
        check (char_length(content) between 1 and 4000),

    constraint ck_wr_comment_mentions
        check (jsonb_typeof(mentions_json) = 'array')
);

create index if not exists
idx_wr_comment_record_time
on work_record.wr_comment(tenant_id, record_id, created_at, id)
where deleted_at is null;

create table if not exists work_record.wr_attachment (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    object_key varchar(512) not null,
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    status varchar(24) not null default 'ready',
    uploaded_by varchar(64) not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,

    constraint uk_wr_attachment_object_key
        unique (object_key),

    constraint ck_wr_attachment_size
        check (size_bytes > 0 and size_bytes <= 20971520),

    constraint ck_wr_attachment_sha256
        check (sha256 ~ '^[0-9a-f]{64}$'),

    constraint ck_wr_attachment_status
        check (status in ('ready', 'quarantined', 'deleted'))
);

create index if not exists
idx_wr_attachment_record
on work_record.wr_attachment(tenant_id, record_id, created_at desc)
where deleted_at is null;

create table if not exists work_record.wr_record_relation (
    id varchar(64) primary key,
    tenant_id varchar(64) not null,
    record_id varchar(64) not null
        references work_record.wr_record(id)
        on delete cascade,
    relation_type varchar(32) not null,
    target_id varchar(64) not null,
    target_title varchar(255),
    target_status varchar(64),
    snapshot_json jsonb not null default '{}'::jsonb,
    created_by varchar(64) not null,
    created_at timestamptz not null default now(),

    constraint uk_wr_record_relation
        unique (tenant_id, record_id, relation_type, target_id),

    constraint ck_wr_record_relation_type
        check (relation_type in ('alert', 'inspection', 'incident')),

    constraint ck_wr_record_relation_snapshot
        check (jsonb_typeof(snapshot_json) = 'object')
);

create index if not exists
idx_wr_record_relation_target
on work_record.wr_record_relation(tenant_id, relation_type, target_id);
```

## 10. Phase 20.3：评论时间线、附件与关联对象

### 10.1 RecordAccessPort.java

扩展模块的所有记录子资源都必须先复用核心记录权限，不得只校验 `recordId` 是否存在。

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface RecordAccessPort {

  WorkRecord requireReadable(
      String tenantId,
      String recordId,
      UserPrincipal principal);

  WorkRecord requireWritable(
      String tenantId,
      String recordId,
      UserPrincipal principal);
}
```

### 10.2 CoreRecordAccessAdapter.java

```java
package io.aegisops.workrecord.extension.infrastructure.adapter;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordPermissionService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import org.springframework.stereotype.Component;

@Component
public class CoreRecordAccessAdapter implements RecordAccessPort {

  private final WorkRecordQueryService queries;
  private final WorkRecordPermissionService permissions;

  public CoreRecordAccessAdapter(
      WorkRecordQueryService queries,
      WorkRecordPermissionService permissions) {
    this.queries = queries;
    this.permissions = permissions;
  }

  @Override
  public WorkRecord requireReadable(
      String tenantId,
      String recordId,
      UserPrincipal principal) {
    return queries.get(tenantId, recordId, principal);
  }

  @Override
  public WorkRecord requireWritable(
      String tenantId,
      String recordId,
      UserPrincipal principal) {
    WorkRecord record = queries.get(tenantId, recordId, principal);
    permissions.requireEdit(principal, record);
    return record;
  }
}
```

### 10.3 WorkRecordComment.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordComment(
    String id,
    String tenantId,
    String recordId,
    String content,
    List<String> mentionUserIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int rowVersion) {

  public WorkRecordComment {
    mentionUserIds = mentionUserIds == null ? List.of() : List.copyOf(mentionUserIds);
  }
}
```

### 10.4 CommentRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {

  WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentions,
      String actorId);

  List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      int limit,
      String afterId);

  Optional<WorkRecordComment> find(
      String tenantId,
      String commentId);

  boolean update(
      String tenantId,
      String commentId,
      String content,
      List<String> mentions,
      int expectedVersion);

  boolean softDelete(
      String tenantId,
      String commentId,
      int expectedVersion);
}
```

### 10.5 JdbcCommentRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCommentRepository implements CommentRepository {

  private static final String COLUMNS =
      "id, tenant_id, record_id, content, mentions_json::text, created_by,"
          + " created_at, updated_at, row_version";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcCommentRepository(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public WorkRecordComment create(
      String tenantId,
      String recordId,
      String content,
      List<String> mentions,
      String actorId) {
    String id = Ids.newId();
    jdbc.update(
        """
        insert into work_record.wr_comment(
          id, tenant_id, record_id, content, mentions_json, created_by)
        values (
          :id, :tenantId, :recordId, :content,
          cast(:mentions as jsonb), :actorId)
        """,
        Map.of(
            "id", id,
            "tenantId", tenantId,
            "recordId", recordId,
            "content", content,
            "mentions", write(mentions),
            "actorId", actorId));
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      int limit,
      String afterId) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("recordId", recordId);
    params.put("afterId", blankToNull(afterId));
    params.put("limit", Math.min(Math.max(limit, 1), 200));

    return jdbc.query(
        "select " + COLUMNS
            + " from work_record.wr_comment"
            + " where tenant_id=:tenantId and record_id=:recordId"
            + " and deleted_at is null"
            + " and (:afterId is null or id > :afterId)"
            + " order by created_at asc, id asc limit :limit",
        params,
        (rs, rowNum) -> map(rs));
  }

  @Override
  public Optional<WorkRecordComment> find(
      String tenantId,
      String commentId) {
    List<WorkRecordComment> rows =
        jdbc.query(
            "select " + COLUMNS
                + " from work_record.wr_comment"
                + " where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", commentId),
            (rs, rowNum) -> map(rs));
    return rows.stream().findFirst();
  }

  @Override
  public boolean update(
      String tenantId,
      String commentId,
      String content,
      List<String> mentions,
      int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
            set content=:content,
                mentions_json=cast(:mentions as jsonb),
                updated_at=now(),
                row_version=row_version+1
            where tenant_id=:tenantId and id=:id
              and row_version=:expectedVersion
              and deleted_at is null
            """,
            Map.of(
                "tenantId", tenantId,
                "id", commentId,
                "content", content,
                "mentions", write(mentions),
                "expectedVersion", expectedVersion))
        == 1;
  }

  @Override
  public boolean softDelete(
      String tenantId,
      String commentId,
      int expectedVersion) {
    return jdbc.update(
            """
            update work_record.wr_comment
            set deleted_at=now(), updated_at=now(), row_version=row_version+1
            where tenant_id=:tenantId and id=:id
              and row_version=:expectedVersion
              and deleted_at is null
            """,
            Map.of(
                "tenantId", tenantId,
                "id", commentId,
                "expectedVersion", expectedVersion))
        == 1;
  }

  private WorkRecordComment map(ResultSet rs) throws SQLException {
    return new WorkRecordComment(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("record_id"),
        rs.getString("content"),
        readMentions(rs.getString("mentions_json")),
        rs.getString("created_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class),
        rs.getObject("updated_at", java.time.OffsetDateTime.class),
        rs.getInt("row_version"));
  }

  private List<String> readMentions(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("invalid comment mentions", ex);
    }
  }

  private String write(List<String> mentions) {
    try {
      return objectMapper.writeValueAsString(mentions == null ? List.of() : mentions);
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid comment mentions", ex);
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
```

### 10.6 CommentService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.WorkRecordComment;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

  private final CommentRepository repository;
  private final RecordAccessPort recordAccess;
  private final UserService users;
  private final NotificationService notifications;
  private final AuditService auditService;

  public CommentService(
      CommentRepository repository,
      RecordAccessPort recordAccess,
      UserService users,
      NotificationService notifications,
      AuditService auditService) {
    this.repository = repository;
    this.recordAccess = recordAccess;
    this.users = users;
    this.notifications = notifications;
    this.auditService = auditService;
  }

  public List<WorkRecordComment> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    return repository.list(tenantId, recordId, 200, null);
  }

  @Transactional
  public WorkRecordComment create(
      String tenantId,
      String recordId,
      CommentCommand command,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    requireCommentPermission(user);
    String content = normalizeContent(command.content());
    List<String> mentions = normalizeMentions(tenantId, command.mentionUserIds());
    WorkRecordComment comment =
        repository.create(tenantId, recordId, content, mentions, user.id());

    for (String mentioned : mentions) {
      if (!mentioned.equals(user.id())) {
        notifications.createMention(
            tenantId,
            mentioned,
            recordId,
            user.displayName(),
            comment.id());
      }
    }

    auditService.record(
        new AuditRecordCommand(
            tenantId,
            user.id(),
            "work_record.comment.create",
            "work_record_comment",
            comment.id(),
            "{}",
            "{}",
            "{\"recordId\":\"" + recordId + "\"}"));
    return comment;
  }

  @Transactional
  public WorkRecordComment update(
      String tenantId,
      String commentId,
      CommentCommand command,
      int expectedVersion,
      UserPrincipal user) {
    WorkRecordComment existing = requireOwned(tenantId, commentId, user);
    recordAccess.requireReadable(tenantId, existing.recordId(), user);
    List<String> mentions = normalizeMentions(tenantId, command.mentionUserIds());
    boolean updated =
        repository.update(
            tenantId,
            commentId,
            normalizeContent(command.content()),
            mentions,
            expectedVersion);
    if (!updated) {
      throw new IllegalStateException("comment was modified by another request");
    }
    return repository.find(tenantId, commentId).orElseThrow();
  }

  @Transactional
  public void delete(
      String tenantId,
      String commentId,
      int expectedVersion,
      UserPrincipal user) {
    WorkRecordComment existing = requireOwned(tenantId, commentId, user);
    recordAccess.requireReadable(tenantId, existing.recordId(), user);
    if (!repository.softDelete(tenantId, commentId, expectedVersion)) {
      throw new IllegalStateException("comment was modified by another request");
    }
  }

  private WorkRecordComment requireOwned(
      String tenantId,
      String commentId,
      UserPrincipal user) {
    WorkRecordComment comment =
        repository.find(tenantId, commentId)
            .orElseThrow(() -> new IllegalArgumentException("comment not found"));
    boolean administrator = user.hasPermission("work-record:comment:moderate");
    if (!administrator && !user.id().equals(comment.createdBy())) {
      throw new AccessDeniedException("not allowed to modify this comment");
    }
    return comment;
  }

  private void requireCommentPermission(UserPrincipal user) {
    if (user == null || !user.hasPermission("work-record:comment")) {
      throw new AccessDeniedException("not allowed to comment");
    }
  }

  private String normalizeContent(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty() || normalized.length() > 4000) {
      throw new IllegalArgumentException("comment length must be between 1 and 4000");
    }
    return normalized;
  }

  private List<String> normalizeMentions(
      String tenantId,
      List<String> values) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (values == null) {
      return List.of();
    }
    if (values.size() > 20) {
      throw new IllegalArgumentException("at most 20 mentions are allowed");
    }
    for (String userId : values) {
      if (userId == null || userId.isBlank()) {
        continue;
      }
      var account = users.getById(userId);
      if (!tenantId.equals(account.tenantId())) {
        throw new IllegalArgumentException("mentioned user is outside tenant");
      }
      result.add(userId);
    }
    return List.copyOf(result);
  }

  public record CommentCommand(
      String content,
      List<String> mentionUserIds) {}
}
```

### 10.7 CommentController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.service.CommentService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records/{recordId}/comments")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class CommentController {

  private final CommentService service;

  public CommentController(CommentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<?> list(
      @PathVariable String recordId,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.list(TenantContext.requireTenantId(), recordId, user));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<?> create(
      @PathVariable String recordId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            recordId,
            new CommentService.CommentCommand(request.content(), request.mentionUserIds()),
            user));
  }

  @PutMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<?> update(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestBody CommentRequest request,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.update(
            TenantContext.requireTenantId(),
            commentId,
            new CommentService.CommentCommand(request.content(), request.mentionUserIds()),
            rowVersion,
            user));
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<Map<String, Boolean>> delete(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal user) {
    service.delete(
        TenantContext.requireTenantId(),
        commentId,
        rowVersion,
        user);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record CommentRequest(
      String content,
      List<String> mentionUserIds) {}
}
```

### 10.8 WorkRecordAttachment.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record WorkRecordAttachment(
    String id,
    String tenantId,
    String recordId,
    String objectKey,
    String fileName,
    String contentType,
    long sizeBytes,
    String sha256,
    String status,
    String uploadedBy,
    OffsetDateTime createdAt) {}
```

### 10.9 AttachmentRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {

  WorkRecordAttachment create(CreateAttachment command);

  List<WorkRecordAttachment> list(String tenantId, String recordId);

  Optional<WorkRecordAttachment> find(String tenantId, String attachmentId);

  boolean markDeleted(String tenantId, String attachmentId);

  record CreateAttachment(
      String id,
      String tenantId,
      String recordId,
      String objectKey,
      String fileName,
      String contentType,
      long sizeBytes,
      String sha256,
      String uploadedBy) {}
}
```

### 10.10 JdbcAttachmentRepository.java

```java
package io.aegisops.workrecord.extension.infrastructure.jdbc;

import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAttachmentRepository implements AttachmentRepository {

  private static final String COLUMNS =
      "id, tenant_id, record_id, object_key, file_name, content_type, size_bytes,"
          + " sha256, status, uploaded_by, created_at";

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAttachmentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public WorkRecordAttachment create(CreateAttachment command) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", command.id());
    params.put("tenantId", command.tenantId());
    params.put("recordId", command.recordId());
    params.put("objectKey", command.objectKey());
    params.put("fileName", command.fileName());
    params.put("contentType", command.contentType());
    params.put("size", command.sizeBytes());
    params.put("sha256", command.sha256());
    params.put("uploadedBy", command.uploadedBy());
    jdbc.update(
        """
        insert into work_record.wr_attachment(
          id, tenant_id, record_id, object_key, file_name, content_type,
          size_bytes, sha256, uploaded_by)
        values (
          :id, :tenantId, :recordId, :objectKey, :fileName, :contentType,
          :size, :sha256, :uploadedBy)
        """,
        params);
    return find(command.tenantId(), command.id()).orElseThrow();
  }

  @Override
  public List<WorkRecordAttachment> list(String tenantId, String recordId) {
    return jdbc.query(
        "select " + COLUMNS
            + " from work_record.wr_attachment"
            + " where tenant_id=:tenantId and record_id=:recordId"
            + " and deleted_at is null and status='ready'"
            + " order by created_at desc, id desc",
        Map.of("tenantId", tenantId, "recordId", recordId),
        (rs, rowNum) -> map(rs));
  }

  @Override
  public Optional<WorkRecordAttachment> find(String tenantId, String attachmentId) {
    List<WorkRecordAttachment> rows =
        jdbc.query(
            "select " + COLUMNS
                + " from work_record.wr_attachment"
                + " where tenant_id=:tenantId and id=:id and deleted_at is null",
            Map.of("tenantId", tenantId, "id", attachmentId),
            (rs, rowNum) -> map(rs));
    return rows.stream().findFirst();
  }

  @Override
  public boolean markDeleted(String tenantId, String attachmentId) {
    return jdbc.update(
            """
            update work_record.wr_attachment
            set status='deleted', deleted_at=now()
            where tenant_id=:tenantId and id=:id and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "id", attachmentId))
        == 1;
  }

  private WorkRecordAttachment map(ResultSet rs) throws SQLException {
    return new WorkRecordAttachment(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("record_id"),
        rs.getString("object_key"),
        rs.getString("file_name"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getString("sha256"),
        rs.getString("status"),
        rs.getString("uploaded_by"),
        rs.getObject("created_at", java.time.OffsetDateTime.class));
  }
}
```

### 10.11 AttachmentService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.domain.WorkRecordAttachment;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttachmentService {

  private static final Set<String> BLOCKED_TYPES =
      Set.of(
          "application/x-msdownload",
          "application/x-sh",
          "application/x-bat",
          "application/java-archive");

  private final RecordAccessPort recordAccess;
  private final AttachmentRepository repository;
  private final ObjectStoragePort storage;
  private final ObjectStorageProperties properties;

  public AttachmentService(
      RecordAccessPort recordAccess,
      AttachmentRepository repository,
      ObjectStoragePort storage,
      ObjectStorageProperties properties) {
    this.recordAccess = recordAccess;
    this.repository = repository;
    this.storage = storage;
    this.properties = properties;
  }

  public List<WorkRecordAttachment> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    recordAccess.requireReadable(tenantId, recordId, user);
    return repository.list(tenantId, recordId);
  }

  @Transactional
  public WorkRecordAttachment upload(
      String tenantId,
      String recordId,
      UploadCommand command,
      UserPrincipal user) {
    recordAccess.requireWritable(tenantId, recordId, user);
    requirePermission(user, "work-record:attachment");
    validate(command);

    String id = Ids.newId();
    String safeName = safeFileName(command.fileName());
    String objectKey =
        tenantId + "/records/" + recordId + "/attachments/" + id + "/" + safeName;
    ObjectStoragePort.StoredObject stored =
        storage.put(
            new ObjectStoragePort.PutObjectCommand(
                objectKey,
                command.contentType(),
                command.sizeBytes()),
            command.input());

    try {
      return repository.create(
          new AttachmentRepository.CreateAttachment(
              id,
              tenantId,
              recordId,
              objectKey,
              command.fileName(),
              command.contentType(),
              stored.sizeBytes(),
              stored.sha256(),
              user.id()));
    } catch (RuntimeException ex) {
      storage.delete(objectKey);
      throw ex;
    }
  }

  public String downloadUrl(
      String tenantId,
      String attachmentId,
      UserPrincipal user) {
    WorkRecordAttachment attachment =
        repository.find(tenantId, attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
    recordAccess.requireReadable(tenantId, attachment.recordId(), user);
    return storage.presignedGet(
        attachment.objectKey(),
        Duration.ofSeconds(properties.downloadUrlExpirySeconds()));
  }

  @Transactional
  public void delete(
      String tenantId,
      String attachmentId,
      UserPrincipal user) {
    WorkRecordAttachment attachment =
        repository.find(tenantId, attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
    recordAccess.requireWritable(tenantId, attachment.recordId(), user);
    if (!attachment.uploadedBy().equals(user.id())
        && !user.hasPermission("work-record:attachment:moderate")) {
      throw new AccessDeniedException("not allowed to delete attachment");
    }
    if (repository.markDeleted(tenantId, attachmentId)) {
      storage.delete(attachment.objectKey());
    }
  }

  private void requirePermission(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new AccessDeniedException("not allowed to upload attachment");
    }
  }

  private void validate(UploadCommand command) {
    if (command.sizeBytes() < 1
        || command.sizeBytes() > properties.attachmentMaxBytes()) {
      throw new IllegalArgumentException("attachment size exceeds limit");
    }
    if (command.contentType() == null || BLOCKED_TYPES.contains(command.contentType())) {
      throw new IllegalArgumentException("attachment content type is not allowed");
    }
  }

  private String safeFileName(String fileName) {
    String value = fileName == null ? "attachment" : fileName;
    value = value.replace('\\', '_').replace('/', '_').replace("..", "_");
    return value.length() <= 120 ? value : value.substring(value.length() - 120);
  }

  public record UploadCommand(
      String fileName,
      String contentType,
      long sizeBytes,
      InputStream input) {}
}
```

生产环境还应在 `stored` 和 `ready` 之间增加杀毒/内容扫描状态；第一版至少拒绝明显可执行类型并记录 SHA-256。

### 10.12 RecordRelation.java

```java
package io.aegisops.workrecord.extension.domain;

import java.time.OffsetDateTime;

public record RecordRelation(
    String id,
    String tenantId,
    String recordId,
    RelationType relationType,
    String targetId,
    String targetTitle,
    String targetStatus,
    String snapshotJson,
    String createdBy,
    OffsetDateTime createdAt) {}
```

### 10.13 RelationType.java

```java
package io.aegisops.workrecord.extension.domain;

public enum RelationType {
  ALERT,
  INSPECTION,
  INCIDENT;

  public String value() {
    return name().toLowerCase();
  }

  public static RelationType from(String value) {
    return RelationType.valueOf(value.trim().toUpperCase());
  }
}
```

### 10.14 RelationTargetPort.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.domain.RelationType;

public interface RelationTargetPort {

  ResolvedTarget resolve(
      String tenantId,
      RelationType type,
      String targetId,
      UserPrincipal principal);

  record ResolvedTarget(
      String id,
      String title,
      String status,
      String snapshotJson) {}
}
```

### 10.15 JdbcRelationTargetAdapter.java

该适配器只读取三个现有领域表，并同时校验调用人的领域权限。当前 alert/incident 表均包含 `tenant_id/title/status`，巡检运行表通过任务表获得名称。

```java
package io.aegisops.workrecord.extension.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.RelationTargetPort;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class JdbcRelationTargetAdapter implements RelationTargetPort {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcRelationTargetAdapter(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResolvedTarget resolve(
      String tenantId,
      RelationType type,
      String targetId,
      UserPrincipal principal) {
    requirePermission(type, principal);
    return switch (type) {
      case ALERT -> one(
          """
          select id, title, status, severity, starts_at
          from alert_event
          where tenant_id=:tenantId and id=:id
          """,
          tenantId,
          targetId);
      case INCIDENT -> one(
          """
          select id, title, status, severity, started_at
          from incident
          where tenant_id=:tenantId and id=:id
          """,
          tenantId,
          targetId);
      case INSPECTION -> inspection(tenantId, targetId);
    };
  }

  private ResolvedTarget inspection(String tenantId, String targetId) {
    List<ResolvedTarget> rows =
        jdbc.query(
            """
            select r.id,
                   t.name as title,
                   r.status,
                   null as severity,
                   r.started_at
            from inspection_run r
            join inspection_task t on t.id=r.task_id
            where r.tenant_id=:tenantId and r.id=:id
            """,
            Map.of("tenantId", tenantId, "id", targetId),
            (rs, rowNum) -> target(rs));
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("inspection run not found"));
  }

  private ResolvedTarget one(
      String sql,
      String tenantId,
      String targetId) {
    List<ResolvedTarget> rows =
        jdbc.query(
            sql,
            Map.of("tenantId", tenantId, "id", targetId),
            (rs, rowNum) -> target(rs));
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("relation target not found"));
  }

  private ResolvedTarget target(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("severity", rs.getString("severity"));
    snapshot.put("startedAt", rs.getObject("started_at"));
    try {
      return new ResolvedTarget(
          rs.getString("id"),
          rs.getString("title"),
          rs.getString("status"),
          objectMapper.writeValueAsString(snapshot));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize relation snapshot", ex);
    }
  }

  private void requirePermission(RelationType type, UserPrincipal principal) {
    String permission =
        switch (type) {
          case ALERT -> "alert:read";
          case INCIDENT -> "incident:read";
          case INSPECTION -> "incident:read";
        };
    if (principal == null || !principal.hasPermission(permission)) {
      throw new AccessDeniedException("not allowed to read relation target");
    }
  }
}
```

### 10.16 RecordRelationService.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.common.id.Ids;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import io.aegisops.workrecord.extension.application.port.RecordRelationRepository;
import io.aegisops.workrecord.extension.application.port.RelationTargetPort;
import io.aegisops.workrecord.extension.domain.RecordRelation;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordRelationService {

  private final RecordAccessPort records;
  private final RelationTargetPort targets;
  private final RecordRelationRepository relations;

  public RecordRelationService(
      RecordAccessPort records,
      RelationTargetPort targets,
      RecordRelationRepository relations) {
    this.records = records;
    this.targets = targets;
    this.relations = relations;
  }

  public List<RecordRelation> list(
      String tenantId,
      String recordId,
      UserPrincipal user) {
    records.requireReadable(tenantId, recordId, user);
    return relations.list(tenantId, recordId);
  }

  @Transactional
  public RecordRelation create(
      String tenantId,
      String recordId,
      RelationType type,
      String targetId,
      UserPrincipal user) {
    records.requireWritable(tenantId, recordId, user);
    if (!user.hasPermission("work-record:relation")) {
      throw new AccessDeniedException("not allowed to create relation");
    }
    RelationTargetPort.ResolvedTarget target =
        targets.resolve(tenantId, type, targetId, user);
    return relations.create(
        new RecordRelationRepository.CreateRelation(
            Ids.newId(),
            tenantId,
            recordId,
            type,
            target.id(),
            target.title(),
            target.status(),
            target.snapshotJson(),
            user.id()));
  }

  @Transactional
  public void delete(
      String tenantId,
      String recordId,
      String relationId,
      UserPrincipal user) {
    records.requireWritable(tenantId, recordId, user);
    if (!relations.delete(tenantId, recordId, relationId)) {
      throw new IllegalArgumentException("relation not found");
    }
  }
}
```

`RecordRelationRepository` 的 JDBC 实现按 `V0032` 字段直接插入、查询、删除，所有 SQL 必须同时包含 `tenant_id` 和 `record_id`。

### 10.17 CommentServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.port.CommentRepository;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommentServiceTest {

  @Test
  void createRequiresRecordVisibilityBeforeWriting() {
    CommentRepository repository = Mockito.mock(CommentRepository.class);
    RecordAccessPort access = Mockito.mock(RecordAccessPort.class);
    UserService users = Mockito.mock(UserService.class);
    NotificationService notifications = Mockito.mock(NotificationService.class);
    AuditService audit = Mockito.mock(AuditService.class);
    var principal = TestPrincipals.commenter();
    when(access.requireReadable("t1", "r1", principal))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));

    CommentService service =
        new CommentService(repository, access, users, notifications, audit);

    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    "r1",
                    new CommentService.CommentCommand("hello", List.of()),
                    principal))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verify(access).requireReadable("t1", "r1", principal);
    Mockito.verifyNoInteractions(repository);
  }
}
```

### 10.18 AttachmentServiceTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.extension.application.port.AttachmentRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.application.port.RecordAccessPort;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AttachmentServiceTest {

  @Test
  void deletesStoredObjectWhenDatabaseInsertFails() {
    RecordAccessPort access = Mockito.mock(RecordAccessPort.class);
    AttachmentRepository repository = Mockito.mock(AttachmentRepository.class);
    ObjectStoragePort storage = Mockito.mock(ObjectStoragePort.class);
    ObjectStorageProperties properties = ObjectStorageProperties.defaults();
    var user = TestPrincipals.attachmentWriter();
    when(storage.put(Mockito.any(), Mockito.any()))
        .thenReturn(new ObjectStoragePort.StoredObject("key", 3, "a".repeat(64)));
    when(repository.create(Mockito.any()))
        .thenThrow(new IllegalStateException("db failed"));

    AttachmentService service =
        new AttachmentService(access, repository, storage, properties);

    assertThatThrownBy(
            () ->
                service.upload(
                    "t1",
                    "r1",
                    new AttachmentService.UploadCommand(
                        "a.txt",
                        "text/plain",
                        3,
                        new ByteArrayInputStream(new byte[] {1, 2, 3})),
                    user))
        .isInstanceOf(IllegalStateException.class);

    verify(storage).delete(Mockito.contains("/records/r1/attachments/"));
  }
}
```

---

## 补充领域模型与 Repository 契约

### 16.6 RecordRelationRepository.java

```java
package io.aegisops.workrecord.extension.application.port;

import io.aegisops.workrecord.extension.domain.RecordRelation;
import io.aegisops.workrecord.extension.domain.RelationType;
import java.util.List;

public interface RecordRelationRepository {

  RecordRelation create(CreateRelation command);

  List<RecordRelation> list(String tenantId, String recordId);

  boolean delete(String tenantId, String recordId, String relationId);

  record CreateRelation(
      String id,
      String tenantId,
      String recordId,
      RelationType relationType,
      String targetId,
      String targetTitle,
      String targetStatus,
      String snapshotJson,
      String createdBy) {}
}
```
