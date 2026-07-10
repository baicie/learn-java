package io.aegisops.workrecord.application.service;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.command.RecordQuickView;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import io.aegisops.workrecord.application.port.WorkRecordWorkMonth;
import io.aegisops.workrecord.application.port.WorkRecordWorkdayWindow;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordQueryService {
  private static final int DEFAULT_WORKDAY_COUNT = 5;
  private static final int MAX_WORKDAY_COUNT = 60;

  private final WorkRecordRepository repository;
  private final WorkRecordPermissionService permissionService;
  private final WorkRecordDynamicFilterPolicyService dynamicFilterPolicyService;
  private final WorkRecordCalendarPort calendarPort;
  private final Clock clock;

  @Autowired
  public WorkRecordQueryService(
      WorkRecordRepository repository,
      WorkRecordPermissionService permissionService,
      WorkRecordDynamicFilterPolicyService dynamicFilterPolicyService,
      WorkRecordCalendarPort calendarPort,
      @Qualifier("workRecordClock") Clock clock) {
    this.repository = repository;
    this.permissionService = permissionService;
    this.dynamicFilterPolicyService = dynamicFilterPolicyService;
    this.calendarPort = calendarPort;
    this.clock = clock;
  }

  WorkRecordQueryService(
      WorkRecordRepository repository, WorkRecordPermissionService permissionService) {
    this(repository, permissionService, null, null, Clock.systemUTC());
  }

  WorkRecordQueryService(
      WorkRecordRepository repository,
      WorkRecordPermissionService permissionService,
      WorkRecordDynamicFilterPolicyService dynamicFilterPolicyService,
      Clock clock) {
    this(repository, permissionService, dynamicFilterPolicyService, null, clock);
  }

  public PageResult<WorkRecord> page(String tenantId, RecordQuery query, UserPrincipal user) {
    return repository.page(tenantId, prepareEffectiveQuery(tenantId, query, user));
  }

  public RecordQuery prepareEffectiveQuery(String tenantId, RecordQuery query, UserPrincipal user) {
    if (query == null) {
      throw new IllegalArgumentException("record query is required");
    }

    boolean canReadAll = permissionService.canReadAll(user);

    if (!canReadAll && !permissionService.canReadSelf(user)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "not allowed to read work records");
    }

    RecordQuickView view = RecordQuickView.from(query.quickView());

    RecordQuery quickQuery = applyQuickView(tenantId, query, view);

    boolean effectiveOnlySelf = !canReadAll || view == RecordQuickView.MINE;

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
            .orElseThrow(() -> new IllegalArgumentException("work record not found"));

    permissionService.requireRead(user, record);

    return record;
  }

  private RecordQuery applyQuickView(String tenantId, RecordQuery query, RecordQuickView view) {
    Instant nowInstant = clock.instant();

    ZonedDateTime now = ZonedDateTime.now(clock);

    OffsetDateTime from = query.recordTimeFrom();

    OffsetDateTime to = query.recordTimeTo();

    switch (view) {
      case MINE, ALL -> {
        // 数据范围统一在 effectiveOnlySelf 中实现。
      }

      case TODAY -> {
        from = now.toLocalDate().atStartOfDay(clock.getZone()).toOffsetDateTime();

        to = from.plusDays(1);
      }

      case THIS_WEEK -> {
        LocalDate start = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);

        from = start.atStartOfDay(clock.getZone()).toOffsetDateTime();

        to = from.plusWeeks(1);
      }

      case THIS_MONTH -> {
        LocalDate start = now.toLocalDate().withDayOfMonth(1);

        from = start.atStartOfDay(clock.getZone()).toOffsetDateTime();

        to = start.plusMonths(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
      }

      case THIS_WORK_MONTH -> {
        WorkRecordWorkMonth month = requireCalendarPort().currentWorkMonth(tenantId, nowInstant);

        ZoneId zoneId = ZoneId.of(month.timeZone());

        from = month.periodStart().atStartOfDay(zoneId).toOffsetDateTime();

        to = month.periodEnd().plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
      }

      case RECENT_WORKDAYS -> {
        int count = normalizeWorkdayCount(query.workdayCount());

        WorkRecordWorkdayWindow window =
            requireCalendarPort().recentWorkdays(tenantId, nowInstant, count);

        ZoneId zoneId = ZoneId.of(window.timeZone());

        from = window.periodStart().atStartOfDay(zoneId).toOffsetDateTime();

        // 保持原有语义：从第 N 个工作日开始到当前时刻。
        // 期间若有人主动填写节假日记录，仍然可见。
        to = OffsetDateTime.ofInstant(nowInstant, zoneId);
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

  private List<RecordDynamicFilter> normalizeDynamicFilters(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<RecordDynamicFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }

    if (dynamicFilterPolicyService == null) {
      throw new IllegalStateException("dynamic filter policy service " + "is unavailable");
    }

    return dynamicFilterPolicyService.normalize(tenantId, templateId, templateVersionId, filters);
  }

  private int normalizeWorkdayCount(Integer requested) {
    if (requested == null) {
      return DEFAULT_WORKDAY_COUNT;
    }

    if (requested < 1 || requested > MAX_WORKDAY_COUNT) {
      throw new IllegalArgumentException("workdayCount must be between 1 and " + MAX_WORKDAY_COUNT);
    }

    return requested;
  }

  private WorkRecordCalendarPort requireCalendarPort() {
    if (calendarPort == null) {
      throw new IllegalStateException("work calendar port is unavailable");
    }

    return calendarPort;
  }
}
