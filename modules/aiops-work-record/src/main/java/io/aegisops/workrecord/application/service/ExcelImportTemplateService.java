package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
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
  private final WorkRecordFieldIndexRepository fields;

  public ExcelImportTemplateService(
      WorkRecordTemplateVersionRepository versions, WorkRecordFieldIndexRepository fields) {
    this.versions = versions;
    this.fields = fields;
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
    List<WorkRecordField> enabledFields =
        fields.listEnabledByVersion(tenantId, templateVersionId).stream()
            .sorted(
                Comparator.comparingInt(WorkRecordField::sortOrder)
                    .thenComparing(WorkRecordField::fieldCode))
            .toList();

    List<ImportColumn> columns = new ArrayList<>(BUILTIN_COLUMNS);
    enabledFields.stream().map(ExcelImportTemplateService::toColumn).forEach(columns::add);

    return new ExcelImportTemplate(
        writeWorkbook(columns),
        "work-record-import-" + safeFileSegment(templateId) + "-v" + version.versionNo() + ".xlsx");
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
        cell.setCellValue(columns.get(index).code());
        cell.setCellStyle(headerStyle);
        records.setColumnWidth(index, 20 * 256);
      }
      records.createFreezePane(0, 1);

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

  private static ImportColumn toColumn(WorkRecordField field) {
    return new ImportColumn(
        field.fieldCode(),
        field.fieldName(),
        field.fieldType().value(),
        field.required(),
        description(field.fieldType()));
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

  private static String safeFileSegment(String value) {
    String safe = value.replaceAll("[^a-zA-Z0-9_-]", "_");
    return safe.length() <= 80 ? safe : safe.substring(0, 80);
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
      String code, String name, String type, boolean required, String description) {}
}
