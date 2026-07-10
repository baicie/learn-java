package io.aegisops.workrecord.application.service;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.RecordQuickView;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordQueryService {
  private final WorkRecordRepository repository;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordDynamicFilterPolicyService dynamicFilterPolicyService;
  private final Clock clock;

  @Autowired
  public WorkRecordQueryService(
      WorkRecordRepository repository,
      WorkRecordPermissionService permissionService,
      WorkRecordDynamicFilterPolicyService dynamicFilterPolicyService,
      @Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.permissionService = permissionService;
    this.dynamicFilterPolicyService = dynamicFilterPolicyService;
    this.clock = clock;
  }

  WorkRecordQueryService(
      WorkRecordRepository repository,
      WorkRecordPermissionService permissionService) {
    this(repository, permissionService, null, Clock.systemUTC());
  }

  public PageResult<WorkRecord> page(String tenantId, RecordQuery query, UserPrincipal user) {
    return repository.page(
        tenantId,
        prepareEffectiveQuery(tenantId, query, user));
  }

  public RecordQuery prepareEffectiveQuery(
      String tenantId,
      RecordQuery query,
      UserPrincipal user) {
    if (query == null) {
      throw new IllegalArgumentException("record query is required");
    }

    boolean permissionOnlySelf =
        !permissionService.canReadAll(user);

    if (!permissionService.canReadAll(user)
        && !permissionService.canReadSelf(user)) {
      throw new SecurityException(
          "not allowed to read work records");
    }

    RecordQuickView view =
        RecordQuickView.from(query.quickView());

    RecordQuery quickQuery =
        applyQuickView(query, view);

    boolean effectiveOnlySelf =
        permissionOnlySelf
            || view == RecordQuickView.MINE;

    List<RecordDynamicFilter> normalizedFilters =
        normalizeDynamicFilters(
            tenantId,
            quickQuery.templateId(),
            quickQuery.templateVersionId(),
            quickQuery.dynamicFilters());

    return new RecordQuery(
        Math.max(1, quickQuery.page()),
        Math.min(Math.max(1, quickQuery.pageSize()), 200),
        quickQuery.templateId(),
        quickQuery.templateVersionId(),
        quickQuery.statuses(),
        quickQuery.keyword(),
        quickQuery.recordTimeFrom(),
        quickQuery.recordTimeTo(),
        quickQuery.creatorId(),
        quickQuery.ownerId(),
        effectiveOnlySelf,
        user == null ? null : user.id(),
        normalizedFilters,
        quickQuery.sortBy(),
        quickQuery.sortDir(),
        view.value(),
        quickQuery.workdayCount());
  }

  public WorkRecord get(String tenantId, String recordId, UserPrincipal user) {
    WorkRecord record =
        repository
            .find(tenantId, recordId)
            .orElseThrow(
                () -> new IllegalArgumentException("work record not found"));

    permissionService.requireRead(user, record);
    return record;
  }

  private List<RecordDynamicFilter> normalizeDynamicFilters(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<RecordDynamicFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }

    if (dynamicFilterPolicyService == null) {
      throw new IllegalStateException("dynamic filter policy service is unavailable");
    }

    return dynamicFilterPolicyService.normalize(
        tenantId, templateId, templateVersionId, filters);
  }

  private RecordQuery applyQuickView(RecordQuery query, RecordQuickView view) {
    ZonedDateTime now = ZonedDateTime.now(clock);
    OffsetDateTime from = query.recordTimeFrom();
    OffsetDateTime to = query.recordTimeTo();

    switch (view) {
      case MINE, ALL -> {
        // MINE 由 effectiveOnlySelf 统一实现 owner OR creator。
      }
      case TODAY -> {
        from = now.toLocalDate().atStartOfDay(clock.getZone()).toOffsetDateTime();
        to = from.plusDays(1);
      }
      case THIS_WEEK -> {
        LocalDate start =
            now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);
        from = start.atStartOfDay(clock.getZone()).toOffsetDateTime();
        to = from.plusWeeks(1);
      }
      case THIS_MONTH -> {
        LocalDate start = now.toLocalDate().withDayOfMonth(1);
        from = start.atStartOfDay(clock.getZone()).toOffsetDateTime();
        to = start.plusMonths(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
      }
      case RECENT_WORKDAYS -> {
        int count =
            query.workdayCount() == null
                ? 5
                : Math.max(1, Math.min(query.workdayCount(), 60));

        LocalDate start = recentWorkdayStart(now.toLocalDate(), count);

        from = start.atStartOfDay(clock.getZone()).toOffsetDateTime();
        to = now.toOffsetDateTime();
      }
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
        query.creatorId(),
        query.ownerId(),
        query.onlySelf(),
        query.currentUserId(),
        query.dynamicFilters(),
        query.sortBy(),
        query.sortDir(),
        view.value(),
        query.workdayCount());
  }

  private LocalDate recentWorkdayStart(LocalDate today, int count) {
    LocalDate cursor = today;
    int remaining = count;

    while (remaining > 0) {
      DayOfWeek day = cursor.getDayOfWeek();
      if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
        remaining -= 1;
      }

      if (remaining > 0) {
        cursor = cursor.minusDays(1);
      }
    }

    return cursor;
  }
}