package io.aegisops.workrecord.application.service;

import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.StatisticsQuery;
import io.aegisops.workrecord.application.command.StatisticsResult;
import io.aegisops.workrecord.application.command.WorkloadSummary;
import io.aegisops.workrecord.application.port.StatisticsRepository;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import java.time.Duration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordStatisticsService {
  private final StatisticsRepository statistics;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordCalendarPort calendar;
  private final FieldPolicyService fieldPolicies;

  public WorkRecordStatisticsService(
      StatisticsRepository statistics,
      WorkRecordFieldIndexRepository fields,
      WorkRecordCalendarPort calendar,
      FieldPolicyService fieldPolicies) {
    this.statistics = statistics;
    this.fields = fields;
    this.calendar = calendar;
    this.fieldPolicies = fieldPolicies;
  }

  public StatisticsResult statistics(
      String tenantId, StatisticsQuery query, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    validateRange(query);
    return statistics.aggregate(tenantId, query, resolveField(tenantId, query, principal));
  }

  public WorkloadSummary workload(String tenantId, StatisticsQuery query, UserPrincipal principal) {
    requirePermission(tenantId, principal);
    validateRange(query);
    int workdays =
        calendar.countWorkdays(tenantId, query.from().toInstant(), query.to().toInstant());
    return statistics.workload(
        tenantId,
        query.templateId(),
        query.from(),
        query.to(),
        workdays,
        resolveField(tenantId, query, principal));
  }

  private StatisticsRepository.StatisticalField resolveField(
      String tenantId, StatisticsQuery query, UserPrincipal principal) {
    if (query.statisticalFieldCode() == null || query.statisticalFieldCode().isBlank()) {
      return StatisticsRepository.StatisticalField.none();
    }
    if (query.templateVersionId() == null || query.templateVersionId().isBlank()) {
      throw new IllegalArgumentException("templateVersionId is required for dynamic statistics");
    }
    if (!fieldPolicies.canReadField(
        tenantId, query.templateVersionId(), query.statisticalFieldCode(), principal)) {
      throw new AccessDeniedException("not allowed to aggregate field");
    }
    var field =
        fields.listByVersion(tenantId, query.templateVersionId()).stream()
            .filter(value -> value.fieldCode().equals(query.statisticalFieldCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("statistical field not found"));
    if (!field.statistical() || field.fieldType() != FieldType.NUMBER) {
      throw new IllegalArgumentException("field is not a statistical number field");
    }
    return new StatisticsRepository.StatisticalField(field.fieldCode(), field.fieldType().value());
  }

  private static void validateRange(StatisticsQuery query) {
    if (query == null
        || query.from() == null
        || query.to() == null
        || !query.to().isAfter(query.from())) {
      throw new IllegalArgumentException("valid statistics time range is required");
    }
    if (Duration.between(query.from(), query.to()).toDays() > 730) {
      throw new IllegalArgumentException("statistics range cannot exceed 730 days");
    }
  }

  private static void requirePermission(String tenantId, UserPrincipal principal) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_ANALYTICS)
        || !principal.hasPermission(PermissionCodes.WORK_RECORD_READ_ALL)) {
      throw new AccessDeniedException("not allowed to read work-record analytics");
    }
  }
}
