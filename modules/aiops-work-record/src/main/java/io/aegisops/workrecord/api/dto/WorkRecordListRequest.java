package io.aegisops.workrecord.api.dto;

import io.aegisops.workrecord.domain.model.DynamicFieldFilter;
import io.aegisops.workrecord.domain.rule.WorkRecordFilterValidator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工作记录列表查询请求。
 *
 * <p>支持内置字段筛选和动态字段筛选。动态字段筛选通过 filters 参数传入， 在 {@link WorkRecordFilterValidator} 中校验 fieldCode 白名单和
 * operator 兼容性。
 */
public record WorkRecordListRequest(
    @Min(value = 1, message = "page must be >= 1") Integer page,
    @Min(value = 1, message = "pageSize must be >= 1")
        @Max(value = 100, message = "pageSize must be <= 100")
        Integer pageSize,
    String templateId,
    List<String> status,
    @Size(max = 255) String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    String creatorId,
    String ownerId,
    List<DynamicFieldFilter> filters,
    String sort) {}
