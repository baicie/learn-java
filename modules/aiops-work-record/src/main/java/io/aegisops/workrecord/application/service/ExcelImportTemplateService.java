package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelImportTemplateService {
  private static final List<ImportColumn> BUILTIN_COLUMNS =
      List.of(
          new ImportColumn("title", "标题", "text", true, "每条记录必填"),
          new ImportColumn("status", "状态", "text", false, "留空时使用导入设置的缺省状态"),
          new ImportColumn("ownerId", "负责人", "user", false, "填写当前租户的用户 ID"),
          new ImportColumn("recordTime", "记录时间", "datetime", true, "填写 ISO 8601 日期时间"));

  private final WorkRecordTemplateVersionRepository versions;
  private final WorkRecordTemplateRepository templates;
  private final WorkRecordFieldIndexRepository fields;
  private final WorkRecordDictionaryPort dictionaries;

  public ExcelImportTemplateService(
      WorkRecordTemplateVersionRepository versions,
      WorkRecordTemplateRepository templates,
      WorkRecordFieldIndexRepository fields,
      WorkRecordDictionaryPort dictionaries) {
    this.versions = versions;
    this.templates = templates;
    this.fields = fields;
    this.dictionaries = dictionaries;
  }

  public ExcelImportTemplate generate(
      String tenantId, String templateId, String templateVersionId) {
    requireText(tenantId, "tenantId");
    requireText(templateId, "templateId");
    requireText(templateVersionId, "templateVersionId");

    var version =
        versions
            .findByTemplateAndVersion(tenantId, templateId, templateVersionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("work record template version not found"));
    var templateName =
        templates
            .find(tenantId, templateId)
            .map(t -> t.name() == null || t.name().isBlank() ? templateId : t.name())
            .orElse(templateId);
    List<WorkRecordField> enabledFields =
        fields.listEnabledByVersion(tenantId, templateVersionId).stream()
            .sorted(
                Comparator.comparingInt(WorkRecordField::sortOrder)
                    .thenComparing(WorkRecordField::fieldCode))
            .toList();

    List<ImportColumn> columns = new ArrayList<>(BUILTIN_COLUMNS);
    Map<String, List<String>> dictionaryValues = new HashMap<>();
    enabledFields.stream()
        .map(field -> toColumn(tenantId, field, dictionaryValues))
        .forEach(columns::add);

    return new ExcelImportTemplate(
        writeWorkbook(columns),
        safeFileSegment(templateName) + "-导入模板-v" + version.versionNo() + ".xlsx");
  }

  private static byte[] writeWorkbook(List<ImportColumn> columns) {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      CellStyle headerStyle = workbook.createCellStyle();
      var headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      Sheet records = workbook.createSheet("records");
      var header = records.createRow(0);
      for (int index = 0; index < columns.size(); index++) {
        var cell = header.createCell(index);
        cell.setCellValue(columns.get(index).header());
        cell.setCellStyle(headerStyle);
        records.setColumnWidth(index, 20 * 256);
      }
      var example = records.createRow(1);
      for (int index = 0; index < columns.size(); index++) {
        example.createCell(index).setCellValue(exampleValue(columns.get(index)));
      }
      records.createFreezePane(0, 1);
      addDictionaryDropdowns(workbook, records, columns);

      Sheet instructions = workbook.createSheet("字段说明");
      var instructionHeader = instructions.createRow(0);
      List<String> headings = List.of("字段编码", "字段名称", "字段类型", "是否必填", "填写说明");
      for (int index = 0; index < headings.size(); index++) {
        var cell = instructionHeader.createCell(index);
        cell.setCellValue(headings.get(index));
        cell.setCellStyle(headerStyle);
      }
      for (int rowIndex = 0; rowIndex < columns.size(); rowIndex++) {
        ImportColumn column = columns.get(rowIndex);
        var row = instructions.createRow(rowIndex + 1);
        row.createCell(0).setCellValue(column.code());
        row.createCell(1).setCellValue(column.name());
        row.createCell(2).setCellValue(column.type());
        row.createCell(3).setCellValue(column.required() ? "是" : "否");
        row.createCell(4).setCellValue(column.description());
      }
      instructions.createFreezePane(0, 1);
      instructions.setColumnWidth(0, 24 * 256);
      instructions.setColumnWidth(1, 24 * 256);
      instructions.setColumnWidth(2, 18 * 256);
      instructions.setColumnWidth(3, 12 * 256);
      instructions.setColumnWidth(4, 48 * 256);

      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("failed to generate Excel import template", ex);
    }
  }

  private ImportColumn toColumn(
      String tenantId, WorkRecordField field, Map<String, List<String>> dictionaryValues) {
    return new ImportColumn(
        field.fieldCode(),
        field.fieldName(),
        field.fieldType().value(),
        field.required(),
        description(field.fieldType()),
        dictionaryValues(tenantId, field, dictionaryValues));
  }

  private List<String> dictionaryValues(
      String tenantId, WorkRecordField field, Map<String, List<String>> cachedValues) {
    if ((field.fieldType() != FieldType.SELECT && field.fieldType() != FieldType.MULTI_SELECT)
        || field.optionSource() != OptionSource.DICT
        || field.dictCode() == null
        || field.dictCode().isBlank()) {
      return List.of();
    }
    return cachedValues.computeIfAbsent(
        field.dictCode(), code -> dictionaries.enabledItemValues(tenantId, code));
  }

  private static void addDictionaryDropdowns(
      XSSFWorkbook workbook, Sheet records, List<ImportColumn> columns) {
    List<Integer> dictionaryColumnIndexes = new ArrayList<>();
    for (int index = 0; index < columns.size(); index++) {
      if (!columns.get(index).dictionaryValues().isEmpty()) {
        dictionaryColumnIndexes.add(index);
      }
    }
    if (dictionaryColumnIndexes.isEmpty()) {
      return;
    }

    Sheet dictionarySheet = workbook.createSheet("字典选项");
    var dictionaryHeader = dictionarySheet.createRow(0);
    var validationHelper = records.getDataValidationHelper();
    for (int optionIndex = 0; optionIndex < dictionaryColumnIndexes.size(); optionIndex++) {
      int recordsColumnIndex = dictionaryColumnIndexes.get(optionIndex);
      ImportColumn column = columns.get(recordsColumnIndex);
      dictionaryHeader.createCell(optionIndex).setCellValue(column.header());
      for (int valueIndex = 0; valueIndex < column.dictionaryValues().size(); valueIndex++) {
        var row = dictionarySheet.getRow(valueIndex + 1);
        if (row == null) {
          row = dictionarySheet.createRow(valueIndex + 1);
        }
        row.createCell(optionIndex).setCellValue(column.dictionaryValues().get(valueIndex));
      }

      String rangeName = "dict_values_" + (optionIndex + 1);
      String excelColumn = CellReference.convertNumToColString(optionIndex);
      var name = workbook.createName();
      name.setNameName(rangeName);
      name.setRefersToFormula(
          "'字典选项'!$"
              + excelColumn
              + "$2:$"
              + excelColumn
              + "$"
              + (column.dictionaryValues().size() + 1));

      var constraint = validationHelper.createFormulaListConstraint(rangeName);
      var regions =
          new CellRangeAddressList(
              1, ExcelImportParser.MAX_ROWS, recordsColumnIndex, recordsColumnIndex);
      DataValidation validation = validationHelper.createValidation(constraint, regions);
      boolean multiSelect = "multi_select".equals(column.type());
      validation.setEmptyCellAllowed(!column.required());
      validation.setShowErrorBox(!multiSelect);
      validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
      if (multiSelect) {
        validation.setShowPromptBox(true);
        validation.createPromptBox("多选字段", "可从列表选择一个值；多个值请使用逗号分隔");
      } else {
        validation.createErrorBox("无效选项", "请从下拉列表选择有效的字典值");
      }
      records.addValidationData(validation);
    }
    workbook.setSheetHidden(workbook.getSheetIndex(dictionarySheet), true);
  }

  private static String description(FieldType fieldType) {
    return switch (fieldType) {
      case TEXT -> "文本";
      case TEXTAREA -> "多行文本";
      case NUMBER -> "数字";
      case DATE -> "日期，格式 YYYY-MM-DD";
      case DATETIME -> "日期时间，格式 ISO 8601";
      case SELECT -> "填写一个有效选项值";
      case MULTI_SELECT -> "多个有效选项值用逗号分隔";
      case USER -> "填写当前租户的用户 ID";
      case BOOLEAN -> "填写 true/false、1/0 或 是/否";
    };
  }

  private static String exampleValue(ImportColumn column) {
    if (!column.dictionaryValues().isEmpty()) {
      return column.dictionaryValues().getFirst();
    }
    return switch (column.code()) {
      case "title" -> "示例工作记录（请替换）";
      case "recordTime" -> "2026-01-01T09:00:00+08:00";
      case "status", "ownerId" -> "";
      default -> exampleByType(column.type());
    };
  }

  private static String exampleByType(String type) {
    return switch (type) {
      case "textarea", "select", "text", "user" -> "示例填写内容";
      case "number" -> "1";
      case "date" -> "2026-01-01";
      case "datetime" -> "2026-01-01T09:00:00+08:00";
      case "boolean" -> "true";
      case "multi_select" -> "选项一,选项二";
      default -> "";
    };
  }

  private static String safeFileSegment(String value) {
    // Windows/Mac 通用非法文件名: / \ : * ? " < > | 以及控制字符
    String safe =
        value
            .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
            .replaceAll("\\s+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    if (safe.isEmpty()) {
      safe = "template";
    }
    // UTF-8 最多 80 个字节，中文 3 字节/字，约 26 个字；截断避免路径过长
    byte[] bytes = safe.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length <= 80) {
      return safe;
    }
    int limit = 80;
    // 确保不在 UTF-8 多字节中间截断
    while (limit > 0 && (bytes[limit] & 0xC0) == 0x80) {
      limit--;
    }
    return new String(bytes, 0, limit, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  public record ExcelImportTemplate(byte[] content, String fileName) {
    public ExcelImportTemplate {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }

  private record ImportColumn(
      String code,
      String name,
      String type,
      boolean required,
      String description,
      List<String> dictionaryValues) {
    private ImportColumn(
        String code, String name, String type, boolean required, String description) {
      this(code, name, type, required, description, List.of());
    }

    String header() {
      return name + " [" + code + "]";
    }
  }
}
