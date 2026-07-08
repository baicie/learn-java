package io.aegisops.workrecord.application;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.api.dto.WorkRecordListRequest;
import io.aegisops.workrecord.domain.model.WorkRecord;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.rule.WorkRecordFilterValidator;
import io.aegisops.workrecord.infrastructure.config.WorkRecordProperties;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordFieldRepository;
import io.aegisops.workrecord.infrastructure.persistence.WorkRecordRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工作记录列表查询服务。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>列表分页查询，支持内置字段筛选和动态字段筛选
 *   <li>权限数据范围过滤（普通用户只看自己和负责的记录）
 *   <li>列表元数据查询（模板列表、动态列、筛选字段）
 * </ul>
 */
@Service
public class WorkRecordQueryApplicationService {

  private final WorkRecordRepository repository;
  private final WorkRecordFieldRepository fieldRepository;
  private final WorkRecordProperties properties;

  public WorkRecordQueryApplicationService(
      WorkRecordRepository repository,
      WorkRecordFieldRepository fieldRepository,
      WorkRecordProperties properties) {
    this.repository = repository;
    this.fieldRepository = fieldRepository;
    this.properties = properties;
  }

  /**
   * 分页查询工作记录。
   *
   * @param tenantId 租户 ID
   * @param user 当前用户
   * @param request 查询请求
   * @return 分页结果
   */
  public PageResult<WorkRecord> list(
      String tenantId, UserPrincipal user, WorkRecordListRequest request) {
    int page = request.page() != null ? request.page() : 1;
    int pageSize = request.pageSize() != null ? request.pageSize() : 20;
    if (pageSize > 100) {
      pageSize = 100;
    }

    boolean canReadAll = hasAuthority(user, "work-record:read:all");

    // 动态字段筛选校验
    if (request.filters() != null && !request.filters().isEmpty()) {
      if (request.templateId() == null || request.templateId().isBlank()) {
        throw new IllegalArgumentException("templateId is required for dynamic filters");
      }
      List<WorkRecordField> fields = fieldRepository.list(tenantId, request.templateId());
      WorkRecordFilterValidator.validate(fields, request.filters());
    }

    return canReadAll
        ? repository.page(
            tenantId,
            page,
            pageSize,
            request.templateId(),
            request.status(),
            request.keyword(),
            request.recordTimeFrom(),
            request.recordTimeTo(),
            request.creatorId(),
            request.ownerId(),
            request.filters())
        : repository.pageForUser(
            tenantId,
            user.id(),
            page,
            pageSize,
            request.templateId(),
            request.status(),
            request.keyword(),
            request.recordTimeFrom(),
            request.recordTimeTo(),
            request.filters());
  }

  /**
   * 获取列表元数据。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID
   * @return 列表元数据
   */
  public RecordListMetadata metadata(String tenantId, String templateId) {
    List<WorkRecordTemplate> templates = repository.listTemplates(tenantId);
    List<RecordListColumn> columns = new ArrayList<>();
    List<RecordListFilterField> filterFields = new ArrayList<>();

    if (templateId != null && !templateId.isBlank()) {
      List<WorkRecordField> fields = fieldRepository.list(tenantId, templateId);
      for (WorkRecordField field : fields) {
        if (!field.enabled()) {
          continue;
        }
        RecordListColumn col =
            new RecordListColumn(
                field.fieldCode(),
                field.fieldName(),
                field.fieldType(),
                field.listVisible(),
                field.filterable(),
                field.exportable(),
                field.statistical(),
                field.sortOrder());
        columns.add(col);

        if (field.filterable()) {
          List<String> operators = allowedOperators(field.fieldType());
          String dictCode = field.dictCode();
          List<RecordListOption> options = new ArrayList<>();
          if ("dict".equals(field.optionSource()) && dictCode != null) {
            // 字典选项由前端注入，前端 list-metadata 响应中不包含字典项内容
          }
          RecordListFilterField ff =
              new RecordListFilterField(
                  field.fieldCode(),
                  field.fieldName(),
                  field.fieldType(),
                  operators,
                  dictCode,
                  field.exportable(),
                  options);
          filterFields.add(ff);
        }
      }
    }

    List<RecordListTemplate> templateList =
        templates.stream().map(t -> new RecordListTemplate(t.id(), t.name(), t.enabled())).toList();

    int maxExportRows = properties.getExport().getMaxRows();
    if (maxExportRows <= 0) {
      maxExportRows = WorkRecordExportApplicationService.DEFAULT_MAX_EXPORT_ROWS;
    }
    return new RecordListMetadata(templateList, columns, filterFields, maxExportRows);
  }

  private boolean hasAuthority(UserPrincipal user, String authority) {
    return user != null
        && user.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  private List<String> allowedOperators(String fieldType) {
    return switch (fieldType) {
      case "text", "textarea" -> List.of("contains", "eq", "exists");
      case "number" -> List.of("eq", "gte", "lte", "between", "exists");
      case "date", "datetime" -> List.of("eq", "gte", "lte", "between", "exists");
      case "select" -> List.of("eq", "in", "exists");
      case "multi_select" -> List.of("in", "exists");
      case "switch", "boolean" -> List.of("eq", "exists");
      case "user" -> List.of("eq", "in", "exists");
      default -> List.of("eq", "contains");
    };
  }

  public record RecordListMetadata(
      List<RecordListTemplate> templates,
      List<RecordListColumn> columns,
      List<RecordListFilterField> filterFields,
      int maxExportRows) {}

  public record RecordListTemplate(String id, String name, boolean enabled) {}

  public record RecordListColumn(
      String fieldCode,
      String label,
      String fieldType,
      boolean listVisible,
      boolean filterable,
      boolean exportable,
      boolean statistical,
      int sortOrder) {}

  public record RecordListFilterField(
      String fieldCode,
      String label,
      String fieldType,
      List<String> operators,
      String dictionaryCode,
      boolean exportable,
      List<RecordListOption> options) {}

  public record RecordListOption(String value, String label) {}
}
