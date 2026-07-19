package io.aegisops.workrecord.application.service;

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
  void parsesBuiltinAndTypedDynamicColumns() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("recordTime");
      header.createCell(2).setCellValue("hours");
      header.createCell(3).setCellValue("completed");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("完成发布");
      row.createCell(1).setCellValue("2026-07-11T10:00:00+08:00");
      row.createCell(2).setCellValue(2.5);
      row.createCell(3).setCellValue("是");
      workbook.write(output);
      content = output.toByteArray();
    }

    var rows =
        parser.parse(
            new ByteArrayInputStream(content),
            List.of(field("hours", FieldType.NUMBER), field("completed", FieldType.BOOLEAN)),
            new ExcelImportParser.ImportDefaults(
                "draft", null, OffsetDateTime.parse("2026-07-11T00:00:00+08:00")));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().title()).isEqualTo("完成发布");
    assertThat(rows.getFirst().customData())
        .containsEntry("hours", new java.math.BigDecimal("2.5"))
        .containsEntry("completed", true);
  }

  @Test
  void parsesReadableHeadersByStableFieldCode() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("标题 [title]");
      header.createCell(1).setCellValue("记录时间 [recordTime]");
      header.createCell(2).setCellValue("工时 [hours]");
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
            new ExcelImportParser.ImportDefaults("draft", null, null));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().title()).isEqualTo("完成发布");
    assertThat(rows.getFirst().customData())
        .containsEntry("hours", new java.math.BigDecimal("2.5"));
  }

  @Test
  void rejectsUnknownAndDuplicateColumns() throws Exception {
    assertThatThrownBy(() -> parseHeaders("title", "unknown_field"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown Excel column");

    assertThatThrownBy(() -> parseHeaders("title", "title"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate Excel column");
  }

  @Test
  void reportsInvalidRowsWithoutDiscardingValidRows() throws Exception {
    byte[] content;
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("records");
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("title");
      header.createCell(1).setCellValue("recordTime");
      var invalid = sheet.createRow(1);
      invalid.createCell(0).setCellValue("");
      invalid.createCell(1).setCellValue("2026-07-11T10:00:00+08:00");
      var valid = sheet.createRow(2);
      valid.createCell(0).setCellValue("有效记录");
      valid.createCell(1).setCellValue("2026-07-11T10:00:00+08:00");
      workbook.write(output);
      content = output.toByteArray();
    }

    var result =
        parser.parseAll(
            new ByteArrayInputStream(content),
            List.of(),
            new ExcelImportParser.ImportDefaults("draft", null, null));

    assertThat(result.rows()).extracting(row -> row.title()).containsExactly("有效记录");
    assertThat(result.failures()).extracting(failure -> failure.rowNumber()).containsExactly(2);
  }

  private void parseHeaders(String... headers) throws Exception {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var row = workbook.createSheet("records").createRow(0);
      for (int index = 0; index < headers.length; index++) {
        row.createCell(index).setCellValue(headers[index]);
      }
      workbook.write(output);
      parser.parse(
          new ByteArrayInputStream(output.toByteArray()),
          List.of(),
          new ExcelImportParser.ImportDefaults("draft", null, OffsetDateTime.now()));
    }
  }

  private static WorkRecordField field(String code, FieldType type) {
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkRecordField(
        "f-" + code,
        "tenant-1",
        "template-1",
        "version-1",
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
