package io.aegisops.workrecord.application.service;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.RecordQuickView;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordQueryService {
  private final WorkRecordRepository repository;
  private final WorkRecordPermissionService permissionService;

  public WorkRecordQueryService(
      WorkRecordRepository repository, WorkRecordPermissionService permissionService) {
    this.repository = repository;
    this.permissionService = permissionService;
  }

  public PageResult<WorkRecord> page(String tenantId, RecordQuery query, UserPrincipal user) {
    boolean onlySelf = !permissionService.canReadAll(user);
    if (onlySelf && !permissionService.canReadSelf(user)) {
      throw new SecurityException("not allowed to read work records");
    }

    RecordQuery quick = applyQuickView(query, user);

    RecordQuery effective =
        new RecordQuery(
            Math.max(1, quick.page()),
            Math.min(Math.max(1, quick.pageSize()), 200),
            quick.templateId(),
            quick.templateVersionId(),
            quick.statuses(),
            quick.keyword(),
            quick.recordTimeFrom(),
            quick.recordTimeTo(),
            quick.creatorId(),
            quick.ownerId(),
            onlySelf,
            user == null ? null : user.id(),
            quick.dynamicFilters(),
            quick.sortBy(),
            quick.sortDir(),
            quick.quickView(),
            quick.workdayCount());
    return repository.page(tenantId, effective);
  }

  public WorkRecord get(String tenantId, String recordId, UserPrincipal user) {
    WorkRecord record =
        repository
            .find(tenantId, recordId)
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));
    permissionService.requireRead(user, record);
    return record;
  }

  private RecordQuery applyQuickView(RecordQuery query, UserPrincipal user) {
    String nowUserId = user == null ? null : user.id();
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime from = query.recordTimeFrom();
    OffsetDateTime to = query.recordTimeTo();
    String ownerId = query.ownerId();
    String creatorId = query.creatorId();

    switch (RecordQuickView.from(query.quickView())) {
      case MINE -> ownerId = nowUserId;
      case TODAY -> {
        from = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        to = from.plusDays(1).minusNanos(1);
      }
      case THIS_WEEK -> {
        var start = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);
        from = start.atStartOfDay().atOffset(now.getOffset());
        to = from.plusDays(7).minusNanos(1);
      }
      case THIS_MONTH -> {
        var start = now.toLocalDate().withDayOfMonth(1);
        from = start.atStartOfDay().atOffset(now.getOffset());
        to = from.plusMonths(1).minusNanos(1);
      }
      case RECENT_WORKDAYS -> {
        int days = query.workdayCount() == null ? 5 : Math.max(1, Math.min(query.workdayCount(), 60));
        from = now.minusDays(days * 2L);
        to = now;
      }
      case ALL -> {}
    }

    return new RecordQuery(
        query.page(),
        query.pageSize(),
        query.templateId(),
        query.templateVersionId(),
        query.statuses(),
        query.keyword(),
        from,
        to,
        creatorId,
        ownerId,
        query.onlySelf(),
        query.currentUserId(),
        query.dynamicFilters(),
        query.sortBy(),
        query.sortDir(),
        query.quickView(),
        query.workdayCount());
  }
}