package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordCommentService;
import io.aegisops.workrecord.domain.model.WorkRecordComment;
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
public class WorkRecordCommentController {
  private final WorkRecordCommentService service;

  public WorkRecordCommentController(WorkRecordCommentService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('work-record:read:self') or hasAuthority('work-record:read:all')")
  public ApiResponse<List<WorkRecordComment>> list(
      @PathVariable String recordId, @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(service.list(TenantContext.requireTenantId(), recordId, principal));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<WorkRecordComment> create(
      @PathVariable String recordId,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.create(
            TenantContext.requireTenantId(),
            recordId,
            new WorkRecordCommentService.CommentCommand(
                request.content(), request.mentionUserIds()),
            principal));
  }

  @PutMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<WorkRecordComment> update(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestParam int rowVersion,
      @RequestBody CommentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(
        service.update(
            TenantContext.requireTenantId(),
            recordId,
            commentId,
            new WorkRecordCommentService.CommentCommand(
                request.content(), request.mentionUserIds()),
            rowVersion,
            principal));
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("hasAuthority('work-record:comment')")
  public ApiResponse<Map<String, Boolean>> delete(
      @PathVariable String recordId,
      @PathVariable String commentId,
      @RequestParam int rowVersion,
      @AuthenticationPrincipal UserPrincipal principal) {
    service.delete(TenantContext.requireTenantId(), recordId, commentId, rowVersion, principal);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record CommentRequest(String content, List<String> mentionUserIds) {}
}
