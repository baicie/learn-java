package io.aegisops.workrecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工作记录导出请求。
 *
 * <p>导出使用 POST body 传递筛选条件，避免 URL token 暴露身份凭据。
 * 导出行数上限由 {@code aiops.work-record.export.max-rows} 配置控制，默认 5000。
 */
public record WorkRecordExportRequest(
    String templateId,
    List<String> status,
    @Size(max = 255) String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    List<DynamicFieldFilter> filters,
    List<String> columns,
    @NotBlank(message = "format is required") String format) {}
