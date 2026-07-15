---
title: Phase 20.2 Excel 导入与异步导出
type: phase
status: draft
phase: work-record-20
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Phase 20.2：Excel 导入与异步导出

> 基线：`baicie/ai-ops`，分支 `feat/record-doc-portal`，提交 `43d16315cc4b68cc705177d2c8faba1d8e42644e`。
>
> 本文件由 Phase 20 总方案按主题拆分。Phase 20 开发前必须先完成 Phase 19 P0 修复。

> 本阶段复用 01 文档中的 V0030、通用异步任务与 MinIO，不重复定义共享表。

## 9. Phase 20.2：Excel 导入与异步导出

### 9.1 依赖调整

`modules/aiops-work-record-extension/pom.xml` 增加：

```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.4.1</version>
</dependency>

<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

`apps/aiops-worker/pom.xml` 增加：

```xml
<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record</artifactId>
  <version>${project.version}</version>
</dependency>

<dependency>
  <groupId>io.aegisops</groupId>
  <artifactId>aiops-work-record-extension</artifactId>
  <version>${project.version}</version>
</dependency>
```

Worker 配置必须显式声明运行角色，避免加载 Server Controller：

```yaml
aiops:
  runtime:
    app: worker
```

所有 Phase 20 Controller 统一增加：

```java
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
```

### 9.2 ExcelImportRequest.java

```java
package io.aegisops.workrecord.extension.application.command;

import java.time.OffsetDateTime;

public record ExcelImportRequest(
    String templateId,
    String templateVersionId,
    String sourceObjectKey,
    String originalFileName,
    String defaultStatus,
    String defaultOwnerId,
    OffsetDateTime defaultRecordTime,
    boolean stopOnError) {}
```

### 9.3 AsyncExportRequest.java

```java
package io.aegisops.workrecord.extension.application.command;

import io.aegisops.workrecord.application.command.RecordDynamicFilter;
import java.time.OffsetDateTime;
import java.util.List;

public record AsyncExportRequest(
    String templateId,
    String templateVersionId,
    List<String> statuses,
    String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    String creatorId,
    String ownerId,
    List<RecordDynamicFilter> dynamicFilters,
    List<String> columns,
    String sortBy,
    String sortDir,
    String quickView,
    Integer workdayCount) {

  public AsyncExportRequest {
    statuses = statuses == null ? List.of() : List.copyOf(statuses);
    dynamicFilters = dynamicFilters == null ? List.of() : List.copyOf(dynamicFilters);
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
```

### 9.4 ImportedRecordRow.java

```java
package io.aegisops.workrecord.extension.application.model;

import java.time.OffsetDateTime;
import java.util.Map;

public record ImportedRecordRow(
    int rowNumber,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    Map<String, Object> customData) {

  public ImportedRecordRow {
    customData = customData == null ? Map.of() : Map.copyOf(customData);
  }
}
```

### 9.5 ImportRowFailure.java

```java
package io.aegisops.workrecord.extension.application.model;

public record ImportRowFailure(
    int rowNumber,
    String fieldCode,
    String errorCode,
    String message) {}
```

### 9.6 ExcelImportParser.java

```java
package io.aegisops.workrecord.extension.application.service;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.model.ImportedRecordRow;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelImportParser {

  private static final int MAX_ROWS = 20_000;
  private static final int MAX_COLUMNS = 300;

  private final DataFormatter formatter = new DataFormatter();

  public List<ImportedRecordRow> parse(
      InputStream input,
      List<WorkRecordField> fields,
      ImportDefaults defaults) {
    try (Workbook workbook = new XSSFWorkbook(input)) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("Excel workbook has no sheet");
      }

      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        throw new IllegalArgumentException("Excel header row is required");
      }

      Map<Integer, ColumnBinding> bindings = bindHeader(header, fields);
      int lastRow = sheet.getLastRowNum();
      if (lastRow - header.getRowNum() > MAX_ROWS) {
        throw new IllegalArgumentException("Excel rows exceed " + MAX_ROWS);
      }

      List<ImportedRecordRow> result = new ArrayList<>();
      for (int index = header.getRowNum() + 1; index <= lastRow; index++) {
        Row row = sheet.getRow(index);
        if (row == null || isBlank(row)) {
          continue;
        }
        result.add(parseRow(row, bindings, defaults));
      }
      return List.copyOf(result);
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to parse Excel import", ex);
    }
  }

  private Map<Integer, ColumnBinding> bindHeader(
      Row header,
      List<WorkRecordField> fields) {
    if (header.getLastCellNum() > MAX_COLUMNS) {
      throw new IllegalArgumentException("Excel columns exceed " + MAX_COLUMNS);
    }

    Map<String, WorkRecordField> fieldsByCode = new HashMap<>();
    for (WorkRecordField field : fields) {
      fieldsByCode.put(field.fieldCode(), field);
    }

    Map<Integer, ColumnBinding> result = new LinkedHashMap<>();
    for (int index = 0; index < header.getLastCellNum(); index++) {
      String name = text(header.getCell(index)).trim();
      if (name.isEmpty()) {
        continue;
      }

      ColumnBinding binding = builtinBinding(name);
      if (binding == null) {
        WorkRecordField field = fieldsByCode.get(name);
        if (field == null) {
          throw new IllegalArgumentException("unknown Excel column: " + name);
        }
        binding = ColumnBinding.custom(field);
      }
      result.put(index, binding);
    }

    boolean hasTitle = result.values().stream().anyMatch(ColumnBinding::title);
    if (!hasTitle) {
      throw new IllegalArgumentException("Excel column title is required");
    }
    return Map.copyOf(result);
  }

  private ImportedRecordRow parseRow(
      Row row,
      Map<Integer, ColumnBinding> bindings,
      ImportDefaults defaults) {
    String title = null;
    String status = defaults.defaultStatus();
    String ownerId = defaults.defaultOwnerId();
    OffsetDateTime recordTime = defaults.defaultRecordTime();
    Map<String, Object> custom = new LinkedHashMap<>();

    for (Map.Entry<Integer, ColumnBinding> entry : bindings.entrySet()) {
      Cell cell = row.getCell(entry.getKey());
      ColumnBinding binding = entry.getValue();
      Object value = value(cell, binding.field());

      if (binding.title()) {
        title = value == null ? null : String.valueOf(value).trim();
      } else if (binding.status()) {
        status = value == null ? status : String.valueOf(value).trim();
      } else if (binding.owner()) {
        ownerId = value == null ? ownerId : String.valueOf(value).trim();
      } else if (binding.recordTime()) {
        recordTime = value == null ? recordTime : toOffsetDateTime(value);
      } else if (binding.field() != null && value != null) {
        custom.put(binding.field().fieldCode(), value);
      }
    }

    if (title == null || title.isBlank()) {
      throw new ImportRowException(row.getRowNum() + 1, "title", "title is required");
    }
    if (recordTime == null) {
      throw new ImportRowException(
          row.getRowNum() + 1,
          "recordTime",
          "recordTime is required");
    }

    return new ImportedRecordRow(
        row.getRowNum() + 1,
        title,
        status,
        emptyToNull(ownerId),
        recordTime,
        custom);
  }

  private Object value(Cell cell, WorkRecordField field) {
    if (cell == null || cell.getCellType() == CellType.BLANK) {
      return null;
    }
    if (field == null) {
      return builtinValue(cell);
    }

    return switch (field.fieldType()) {
      case NUMBER -> number(cell);
      case BOOLEAN -> bool(cell);
      case DATE -> date(cell).toString();
      case DATETIME -> datetime(cell).toString();
      case MULTI_SELECT -> multi(text(cell));
      default -> text(cell).trim();
    };
  }

  private Object builtinValue(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC);
    }
    return text(cell).trim();
  }

  private BigDecimal number(Cell cell) {
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
    }
    try {
      return new BigDecimal(text(cell).trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid number: " + text(cell), ex);
    }
  }

  private boolean bool(Cell cell) {
    if (cell.getCellType() == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }
    String value = text(cell).trim().toLowerCase();
    return switch (value) {
      case "true", "1", "yes", "y", "是" -> true;
      case "false", "0", "no", "n", "否" -> false;
      default -> throw new IllegalArgumentException("invalid boolean: " + value);
    };
  }

  private LocalDate date(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().toLocalDate();
    }
    return LocalDate.parse(text(cell).trim());
  }

  private OffsetDateTime datetime(Cell cell) {
    if (DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC);
    }
    return OffsetDateTime.parse(text(cell).trim());
  }

  private OffsetDateTime toOffsetDateTime(Object value) {
    if (value instanceof OffsetDateTime dateTime) {
      return dateTime;
    }
    return OffsetDateTime.parse(String.valueOf(value));
  }

  private List<String> multi(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split("[,;，；]"))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .distinct()
        .toList();
  }

  private boolean isBlank(Row row) {
    for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
      Cell cell = row.getCell(index);
      if (cell != null && !text(cell).isBlank()) {
        return false;
      }
    }
    return true;
  }

  private String text(Cell cell) {
    return cell == null ? "" : formatter.formatCellValue(cell);
  }

  private ColumnBinding builtinBinding(String name) {
    return switch (name) {
      case "title" -> ColumnBinding.titleBinding();
      case "status" -> ColumnBinding.statusBinding();
      case "ownerId" -> ColumnBinding.ownerBinding();
      case "recordTime" -> ColumnBinding.recordTimeBinding();
      default -> null;
    };
  }

  private String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record ImportDefaults(
      String defaultStatus,
      String defaultOwnerId,
      OffsetDateTime defaultRecordTime) {}

  private record ColumnBinding(
      boolean title,
      boolean status,
      boolean owner,
      boolean recordTime,
      WorkRecordField field) {

    static ColumnBinding titleBinding() {
      return new ColumnBinding(true, false, false, false, null);
    }

    static ColumnBinding statusBinding() {
      return new ColumnBinding(false, true, false, false, null);
    }

    static ColumnBinding ownerBinding() {
      return new ColumnBinding(false, false, true, false, null);
    }

    static ColumnBinding recordTimeBinding() {
      return new ColumnBinding(false, false, false, true, null);
    }

    static ColumnBinding custom(WorkRecordField field) {
      return new ColumnBinding(false, false, false, false, field);
    }
  }

  public static final class ImportRowException extends IllegalArgumentException {
    private final int rowNumber;
    private final String fieldCode;

    public ImportRowException(int rowNumber, String fieldCode, String message) {
      super(message);
      this.rowNumber = rowNumber;
      this.fieldCode = fieldCode;
    }

    public int rowNumber() {
      return rowNumber;
    }

    public String fieldCode() {
      return fieldCode;
    }
  }
}
```

### 9.7 WorkRecordBatchWritePort.java

该 Port 放在 `aiops-work-record`，供可信 Worker 使用；不能让 Worker 直接绕过领域校验写表。

```java
package io.aegisops.workrecord.application.port;

import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.domain.model.WorkRecord;

public interface WorkRecordBatchWritePort {

  WorkRecord createValidated(
      String tenantId,
      CreateRecordCommand command,
      String actorId);
}
```

### 9.8 WorkRecordBatchWriteService.java

```java
package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordBatchWritePort;
import io.aegisops.workrecord.domain.model.WorkRecord;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordBatchWriteService implements WorkRecordBatchWritePort {

  private final WorkRecordTrustedMutationService trustedMutationService;

  public WorkRecordBatchWriteService(
      WorkRecordTrustedMutationService trustedMutationService) {
    this.trustedMutationService = trustedMutationService;
  }

  @Override
  public WorkRecord createValidated(
      String tenantId,
      CreateRecordCommand command,
      String actorId) {
    return trustedMutationService.create(tenantId, command, actorId);
  }
}
```

`WorkRecordTrustedMutationService` 必须复用 `WorkRecordService.create` 中的版本解析、字段校验、字典校验、用户校验与审计逻辑，只移除 HTTP 权限检查。推荐将当前 `WorkRecordService.create` 重构为：

```java
public WorkRecord create(
    String tenantId,
    CreateRecordCommand command,
    UserPrincipal user) {
  permissionService.requireCreate(user);
  return trustedMutationService.create(
      tenantId,
      command,
      user.id());
}
```

### 9.9 WorkRecordImportProcessor.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.CreateRecordCommand;
import io.aegisops.workrecord.application.port.WorkRecordBatchWritePort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.extension.application.command.ExcelImportRequest;
import io.aegisops.workrecord.extension.application.model.ImportRowFailure;
import io.aegisops.workrecord.extension.application.model.ImportedRecordRow;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.JobCompletion;
import io.aegisops.workrecord.extension.domain.JobProgress;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordImportProcessor {

  private final AsyncJobRepository jobs;
  private final ObjectStoragePort storage;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordBatchWritePort batchWriter;
  private final ExcelImportParser parser;
  private final ObjectMapper objectMapper;

  public WorkRecordImportProcessor(
      AsyncJobRepository jobs,
      ObjectStoragePort storage,
      WorkRecordFieldIndexRepository fields,
      WorkRecordBatchWritePort batchWriter,
      ExcelImportParser parser,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.storage = storage;
    this.fields = fields;
    this.batchWriter = batchWriter;
    this.parser = parser;
    this.objectMapper = objectMapper;
  }

  public void process(String tenantId, String jobId) {
    var job = jobs.find(tenantId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (!jobs.markRunning(tenantId, jobId)) {
      return;
    }

    try {
      ExcelImportRequest request =
          objectMapper.readValue(job.requestJson(), ExcelImportRequest.class);
      List<WorkRecordField> versionFields =
          fields.listByVersion(tenantId, request.templateVersionId());

      List<ImportedRecordRow> rows;
      try (InputStream input = storage.get(request.sourceObjectKey())) {
        rows = parser.parse(
            input,
            versionFields,
            new ExcelImportParser.ImportDefaults(
                request.defaultStatus(),
                request.defaultOwnerId(),
                request.defaultRecordTime()));
      }

      List<ImportRowFailure> failures = new ArrayList<>();
      int success = 0;
      for (ImportedRecordRow row : rows) {
        try {
          batchWriter.createValidated(
              tenantId,
              new CreateRecordCommand(
                  request.templateId(),
                  request.templateVersionId(),
                  row.title(),
                  row.status(),
                  row.ownerId(),
                  row.recordTime(),
                  "{}",
                  objectMapper.writeValueAsString(row.customData())),
              job.requestedBy());
          success++;
        } catch (RuntimeException ex) {
          failures.add(
              new ImportRowFailure(
                  row.rowNumber(),
                  null,
                  ex.getClass().getSimpleName(),
                  safeMessage(ex)));
          if (request.stopOnError()) {
            break;
          }
        }

        jobs.updateProgress(
            tenantId,
            jobId,
            new JobProgress(rows.size(), success + failures.size(), success, failures.size()));
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("failures", failures);
      result.put("successCount", success);
      result.put("failureCount", failures.size());

      JobProgress progress =
          new JobProgress(rows.size(), success + failures.size(), success, failures.size());
      jobs.complete(
          tenantId,
          jobId,
          new JobCompletion(
              failures.isEmpty()
                  ? AsyncJobStatus.SUCCEEDED
                  : AsyncJobStatus.PARTIAL_SUCCESS,
              objectMapper.writeValueAsString(result),
              null,
              null,
              null,
              progress));
    } catch (Exception ex) {
      jobs.fail(tenantId, jobId, safeMessage(ex));
      throw new IllegalStateException("work-record import failed", ex);
    }
  }

  private String safeMessage(Throwable ex) {
    String value = ex.getMessage();
    if (value == null || value.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
```

### 9.10 WorkRecordImportJob.java

```java
package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.workrecord.extension.application.service.WorkRecordImportProcessor;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordImportJob implements OutboxJob {

  public static final String JOB_NAME = "work-record-import";

  private final WorkRecordImportProcessor processor;
  private final ObjectMapper objectMapper;

  public WorkRecordImportJob(
      WorkRecordImportProcessor processor,
      ObjectMapper objectMapper) {
    this.processor = processor;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      processor.process(
          required(payload, "tenantId"),
          required(payload, "jobId"));
      return JobResult.success();
    } catch (RuntimeException ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    } catch (Exception ex) {
      return JobResult.failure("INVALID_PAYLOAD");
    }
  }

  private String required(JsonNode payload, String name) {
    String value = payload.path(name).asText();
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
```

### 9.11 WorkRecordImportService.java

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.command.ExcelImportRequest;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobType;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordImportService {

  private final ObjectStoragePort storage;
  private final AsyncJobService asyncJobs;
  private final ObjectMapper objectMapper;

  public WorkRecordImportService(
      ObjectStoragePort storage,
      AsyncJobService asyncJobs,
      ObjectMapper objectMapper) {
    this.storage = storage;
    this.asyncJobs = asyncJobs;
    this.objectMapper = objectMapper;
  }

  public String submit(
      String tenantId,
      ImportSubmission submission,
      UserPrincipal user) {
    requirePermission(user, "work-record:import");
    validateFile(submission);

    String jobId = io.aegisops.common.id.Ids.newId();
    String objectKey =
        tenantId + "/imports/" + jobId + "/source.xlsx";

    storage.put(
        new ObjectStoragePort.PutObjectCommand(
            objectKey,
            submission.contentType(),
            submission.sizeBytes()),
        submission.input());

    ExcelImportRequest request =
        new ExcelImportRequest(
            submission.templateId(),
            submission.templateVersionId(),
            objectKey,
            submission.originalFileName(),
            submission.defaultStatus(),
            submission.defaultOwnerId(),
            submission.defaultRecordTime(),
            submission.stopOnError());

    try {
      return asyncJobs.create(
          tenantId,
          new CreateAsyncJobCommand(
              jobId,
              AsyncJobType.RECORD_IMPORT,
              objectMapper.writeValueAsString(request),
              objectKey,
              "import:" + tenantId + ":" + jobId,
              OffsetDateTime.now().plusDays(7)),
          user,
          "work-record-import");
    } catch (Exception ex) {
      storage.delete(objectKey);
      throw new IllegalStateException("failed to create import job", ex);
    }
  }

  private void validateFile(ImportSubmission submission) {
    if (submission.sizeBytes() < 1 || submission.sizeBytes() > 20L * 1024L * 1024L) {
      throw new IllegalArgumentException("Excel file must be between 1 byte and 20 MiB");
    }
    String fileName = submission.originalFileName();
    if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
      throw new IllegalArgumentException("only .xlsx is supported");
    }
  }

  private void requirePermission(UserPrincipal user, String permission) {
    if (user == null || !user.hasPermission(permission)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "not allowed to import work records");
    }
  }

  public record ImportSubmission(
      String templateId,
      String templateVersionId,
      String originalFileName,
      String contentType,
      long sizeBytes,
      InputStream input,
      String defaultStatus,
      String defaultOwnerId,
      OffsetDateTime defaultRecordTime,
      boolean stopOnError) {}
}
```

### 9.12 WorkRecordImportController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.service.WorkRecordImportService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/work-record/imports")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class WorkRecordImportController {

  private final WorkRecordImportService service;

  public WorkRecordImportController(WorkRecordImportService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('work-record:import')")
  public ApiResponse<Map<String, String>> submit(
      @RequestPart("file") MultipartFile file,
      @RequestParam String templateId,
      @RequestParam String templateVersionId,
      @RequestParam(defaultValue = "draft") String defaultStatus,
      @RequestParam(required = false) String defaultOwnerId,
      @RequestParam(required = false) OffsetDateTime defaultRecordTime,
      @RequestParam(defaultValue = "false") boolean stopOnError,
      @AuthenticationPrincipal UserPrincipal user)
      throws IOException {
    String jobId =
        service.submit(
            TenantContext.requireTenantId(),
            new WorkRecordImportService.ImportSubmission(
                templateId,
                templateVersionId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                defaultStatus,
                defaultOwnerId,
                defaultRecordTime,
                stopOnError),
            user);
    return ApiResponse.ok(Map.of("jobId", jobId));
  }
}
```

### 9.13 WorkRecordAsyncExportProcessor.java

异步导出不能直接调用现有返回 `byte[]` 的同步导出方法，否则仍会把完整文件留在内存中。将现有列解析、字典 label、历史版本字段映射抽到 `WorkRecordExportDatasetService`，再由同步和异步两个 Writer 复用。

```java
package io.aegisops.workrecord.extension.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.service.WorkRecordExportDatasetService;
import io.aegisops.workrecord.extension.application.command.AsyncExportRequest;
import io.aegisops.workrecord.extension.application.port.AsyncJobRepository;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobStatus;
import io.aegisops.workrecord.extension.domain.JobCompletion;
import io.aegisops.workrecord.extension.domain.JobProgress;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordAsyncExportProcessor {

  private final AsyncJobRepository jobs;
  private final WorkRecordExportDatasetService datasets;
  private final ObjectStoragePort storage;
  private final ObjectMapper objectMapper;
  private final ExecutorService writerExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public WorkRecordAsyncExportProcessor(
      AsyncJobRepository jobs,
      WorkRecordExportDatasetService datasets,
      ObjectStoragePort storage,
      ObjectMapper objectMapper) {
    this.jobs = jobs;
    this.datasets = datasets;
    this.storage = storage;
    this.objectMapper = objectMapper;
  }

  public void process(
      String tenantId,
      String jobId,
      UserPrincipal principal) {
    var job = jobs.find(tenantId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("export job not found"));
    if (!jobs.markRunning(tenantId, jobId)) {
      return;
    }

    String objectKey = tenantId + "/exports/" + jobId + "/records.csv";
    String fileName = "work-records-" + jobId + ".csv";

    try {
      AsyncExportRequest request =
          objectMapper.readValue(job.requestJson(), AsyncExportRequest.class);
      RecordQuery query = toQuery(request, principal.id());
      var dataset = datasets.prepare(
          tenantId,
          query,
          request.columns(),
          principal,
          100_000);

      try (PipedInputStream input = new PipedInputStream(128 * 1024);
          PipedOutputStream output = new PipedOutputStream(input)) {
        var writerFuture = writerExecutor.submit(() -> {
          try (var writer =
              new java.io.BufferedWriter(
                  new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            datasets.writeCsv(dataset, writer, progress ->
                jobs.updateProgress(tenantId, jobId, progress));
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        });

        storage.putUnknownLength(
            objectKey,
            "text/csv;charset=UTF-8",
            input,
            100L * 1024L * 1024L);
        writerFuture.get();
      }

      JobProgress progress =
          new JobProgress(
              dataset.totalCount(),
              dataset.totalCount(),
              dataset.totalCount(),
              0);
      jobs.complete(
          tenantId,
          jobId,
          new JobCompletion(
              AsyncJobStatus.SUCCEEDED,
              objectMapper.writeValueAsString(Map.of("rowCount", dataset.totalCount())),
              objectKey,
              fileName,
              "text/csv;charset=UTF-8",
              progress));
    } catch (Exception ex) {
      storage.delete(objectKey);
      jobs.fail(tenantId, jobId, safe(ex));
      throw new IllegalStateException("async export failed", ex);
    }
  }

  private RecordQuery toQuery(AsyncExportRequest request, String userId) {
    return new RecordQuery(
        1,
        500,
        request.templateId(),
        request.templateVersionId(),
        request.statuses(),
        request.keyword(),
        request.recordTimeFrom(),
        request.recordTimeTo(),
        request.creatorId(),
        request.ownerId(),
        false,
        userId,
        request.dynamicFilters(),
        request.sortBy(),
        request.sortDir(),
        request.quickView(),
        request.workdayCount());
  }

  private String safe(Throwable ex) {
    String value = ex.getMessage();
    return value == null ? ex.getClass().getSimpleName() : value.substring(0, Math.min(500, value.length()));
  }
}
```

`ObjectStoragePort` 增加：

```java
StoredObject putUnknownLength(
    String objectKey,
    String contentType,
    InputStream input,
    long maxBytes);
```

MinIO 实现必须在读取流时累计字节并在超过 `maxBytes` 后中断，不能把未知长度流无限写入。

### 9.14 WorkRecordAsyncExportJob.java

```java
package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.service.WorkRecordAsyncExportProcessor;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordAsyncExportJob implements OutboxJob {

  public static final String JOB_NAME = "work-record-export";

  private final WorkRecordAsyncExportProcessor processor;
  private final UserService users;
  private final UserPrincipalFactory principals;
  private final ObjectMapper objectMapper;

  public WorkRecordAsyncExportJob(
      WorkRecordAsyncExportProcessor processor,
      UserService users,
      UserPrincipalFactory principals,
      ObjectMapper objectMapper) {
    this.processor = processor;
    this.users = users;
    this.principals = principals;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      String tenantId = required(payload, "tenantId");
      String jobId = required(payload, "jobId");
      String requestedBy = required(payload, "requestedBy");
      var user = users.getById(requestedBy);
      if (!tenantId.equals(user.tenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      var principal = principals.create(user);
      if (!principal.hasPermission("work-record:export")) {
        return JobResult.failure("EXPORT_PERMISSION_REVOKED");
      }
      processor.process(tenantId, jobId, principal);
      return JobResult.success();
    } catch (RuntimeException ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    } catch (Exception ex) {
      return JobResult.failure("INVALID_PAYLOAD");
    }
  }

  private String required(JsonNode payload, String name) {
    String value = payload.path(name).asText();
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
```

异步执行时重新读取用户授权，确保用户提交任务后权限被撤销时，Worker 不会继续导出。

### 9.15 AsyncJobController.java

```java
package io.aegisops.workrecord.extension.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.extension.application.command.AsyncExportRequest;
import io.aegisops.workrecord.extension.application.command.CreateAsyncJobCommand;
import io.aegisops.workrecord.extension.application.service.AsyncJobService;
import io.aegisops.workrecord.extension.application.port.ObjectStoragePort;
import io.aegisops.workrecord.extension.domain.AsyncJobType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/jobs")
@ConditionalOnProperty(
    prefix = "aiops.runtime",
    name = "app",
    havingValue = "server",
    matchIfMissing = true)
public class AsyncJobController {

  private final AsyncJobService jobs;
  private final ObjectStoragePort storage;

  public AsyncJobController(
      AsyncJobService jobs,
      ObjectStoragePort storage) {
    this.jobs = jobs;
    this.storage = storage;
  }

  @PostMapping("/exports")
  @PreAuthorize("hasAuthority('work-record:export:async')")
  public ApiResponse<Map<String, String>> export(
      @RequestBody AsyncExportRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    String tenantId = TenantContext.requireTenantId();
    String jobId = io.aegisops.common.id.Ids.newId();
    String created =
        jobs.createJson(
            tenantId,
            new CreateAsyncJobCommand(
                jobId,
                AsyncJobType.RECORD_EXPORT,
                request,
                null,
                "export:" + tenantId + ":" + user.id() + ":" + jobId,
                OffsetDateTime.now().plusDays(7)),
            user,
            "work-record-export");
    return ApiResponse.ok(Map.of("jobId", created));
  }

  @GetMapping
  public ApiResponse<List<?>> list(
      @AuthenticationPrincipal UserPrincipal user) {
    String tenantId = TenantContext.requireTenantId();
    String requestedBy = user.hasPermission("work-record:read:all") ? null : user.id();
    return ApiResponse.ok(jobs.list(tenantId, requestedBy, 100));
  }

  @GetMapping("/{jobId}")
  public ApiResponse<?> get(
      @PathVariable String jobId,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        jobs.getVisible(
            TenantContext.requireTenantId(),
            jobId,
            user));
  }

  @GetMapping("/{jobId}/download-url")
  public ApiResponse<Map<String, String>> download(
      @PathVariable String jobId,
      @AuthenticationPrincipal UserPrincipal user) {
    var job = jobs.getVisible(TenantContext.requireTenantId(), jobId, user);
    if (!job.status().downloadable() || job.resultObjectKey() == null) {
      throw new IllegalStateException("job result is not downloadable");
    }
    return ApiResponse.ok(
        Map.of(
            "url",
            storage.presignedGet(job.resultObjectKey(), Duration.ofMinutes(5)),
            "fileName",
            job.resultFileName()));
  }
}
```

### 9.16 ExcelImportParserTest.java

```java
package io.aegisops.workrecord.extension.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportParserTest {

  private final ExcelImportParser parser = new ExcelImportParser();

  @Test
  void parsesBuiltinAndDynamicColumns() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("recordTime");
      header.createCell(2).setCellValue("hours");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("完成发布");
      row.createCell(1).setCellValue("2026-07-11T10:00:00+08:00");
      row.createCell(2).setCellValue(2.5);
      workbook.write(output);
      content = output.toByteArray();
    }

    var rows =
        parser.parse(
            new ByteArrayInputStream(content),
            List.of(field("hours", FieldType.NUMBER)),
            new ExcelImportParser.ImportDefaults(
                "draft",
                null,
                OffsetDateTime.parse("2026-07-11T00:00:00+08:00")));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().title()).isEqualTo("完成发布");
    assertThat(rows.getFirst().customData().get("hours").toString())
        .isEqualTo("2.5");
  }

  @Test
  void rejectsUnknownColumn() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("unknown_field");
      workbook.write(output);
      content = output.toByteArray();
    }

    assertThatThrownBy(
            () ->
                parser.parse(
                    new ByteArrayInputStream(content),
                    List.of(),
                    new ExcelImportParser.ImportDefaults(
                        "draft",
                        null,
                        OffsetDateTime.now())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown Excel column");
  }

  private WorkRecordField field(String code, FieldType type) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "t1",
        "tpl1",
        "v1",
        code,
        code,
        type,
        false,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        true,
        0,
        true,
        now,
        now);
  }
}
```

### 9.17 WorkRecordAsyncExportJobTest.java

```java
package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.extension.application.service.WorkRecordAsyncExportProcessor;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordAsyncExportJobTest {

  @Test
  void reloadsAuthorizationBeforeExport() {
    var processor = Mockito.mock(WorkRecordAsyncExportProcessor.class);
    var users = Mockito.mock(UserService.class);
    var principals = Mockito.mock(UserPrincipalFactory.class);
    var principal = TestPrincipals.exporter();
    UserAccount account = TestUsers.account("u1", "t1");
    when(users.getById("u1")).thenReturn(account);
    when(principals.create(account)).thenReturn(principal);

    var job =
        new WorkRecordAsyncExportJob(
            processor,
            users,
            principals,
            new ObjectMapper());
    AutomationOutboxRecord row = new AutomationOutboxRecord();
    row.setPayload(
        JSONB.jsonb("{\"tenantId\":\"t1\",\"jobId\":\"j1\",\"requestedBy\":\"u1\"}"));

    assertThat(job.handle(row).isSuccess()).isTrue();
    verify(processor).process("t1", "j1", principal);
  }
}
```

---
