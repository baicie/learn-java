package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.support.WorkRecordFixtures;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportTemplateServiceTest {
  private final WorkRecordTemplateVersionRepository versions =
      mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fields = mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordDictionaryPort dictionaries = mock(WorkRecordDictionaryPort.class);
  private final ExcelImportTemplateService service =
      new ExcelImportTemplateService(versions, fields, dictionaries);

  @Test
  void generatesWorkbookFromEnabledFieldsInTemplateOrder() throws Exception {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-1"))
        .thenReturn(Optional.of(WorkRecordFixtures.version("version-1")));
    when(fields.listEnabledByVersion("tenant-1", "version-1"))
        .thenReturn(
            List.of(
                field("result", "处理结果", FieldType.TEXTAREA, true, 20),
                field("hours", "工时", FieldType.NUMBER, false, 10)));

    var template = service.generate("tenant-1", "template-1", "version-1");

    assertThat(template.fileName()).isEqualTo("work-record-import-template-1-v1.xlsx");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template.content()))) {
      var records = workbook.getSheet("records");
      assertThat(records).isNotNull();
      assertThat(
              List.of(
                  records.getRow(0).getCell(0).getStringCellValue(),
                  records.getRow(0).getCell(1).getStringCellValue(),
                  records.getRow(0).getCell(2).getStringCellValue(),
                  records.getRow(0).getCell(3).getStringCellValue(),
                  records.getRow(0).getCell(4).getStringCellValue(),
                  records.getRow(0).getCell(5).getStringCellValue()))
          .containsExactly(
              "标题 [title]",
              "状态 [status]",
              "负责人 [ownerId]",
              "记录时间 [recordTime]",
              "工时 [hours]",
              "处理结果 [result]");
      assertThat(
              List.of(
                  records.getRow(1).getCell(0).getStringCellValue(),
                  records.getRow(1).getCell(1).getStringCellValue(),
                  records.getRow(1).getCell(2).getStringCellValue(),
                  records.getRow(1).getCell(3).getStringCellValue(),
                  records.getRow(1).getCell(4).getStringCellValue(),
                  records.getRow(1).getCell(5).getStringCellValue()))
          .containsExactly("示例工作记录（请替换）", "", "", "2026-01-01T09:00:00+08:00", "1", "示例填写内容");

      var instructions = workbook.getSheet("字段说明");
      assertThat(instructions).isNotNull();
      assertThat(instructions.getRow(5).getCell(0).getStringCellValue()).isEqualTo("hours");
      assertThat(instructions.getRow(5).getCell(1).getStringCellValue()).isEqualTo("工时");
      assertThat(instructions.getRow(5).getCell(2).getStringCellValue()).isEqualTo("number");
      assertThat(instructions.getRow(5).getCell(3).getStringCellValue()).isEqualTo("否");
      assertThat(instructions.getRow(6).getCell(0).getStringCellValue()).isEqualTo("result");
      assertThat(instructions.getRow(6).getCell(3).getStringCellValue()).isEqualTo("是");
    }
  }

  @Test
  void generatesExamplesForEverySupportedFieldType() throws Exception {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-1"))
        .thenReturn(Optional.of(WorkRecordFixtures.version("version-1")));
    when(fields.listEnabledByVersion("tenant-1", "version-1"))
        .thenReturn(
            List.of(
                field("text", "Text", FieldType.TEXT, false, 10),
                field("textarea", "Textarea", FieldType.TEXTAREA, false, 20),
                field("number", "Number", FieldType.NUMBER, false, 30),
                field("date", "Date", FieldType.DATE, false, 40),
                field("datetime", "Datetime", FieldType.DATETIME, false, 50),
                field("select", "Select", FieldType.SELECT, false, 60),
                field("multi-select", "Multi-select", FieldType.MULTI_SELECT, false, 70),
                field("user", "User", FieldType.USER, false, 80),
                field("boolean", "Boolean", FieldType.BOOLEAN, false, 90)));

    var template = service.generate("tenant-1", "template-1", "version-1");

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template.content()))) {
      var records = workbook.getSheet("records");
      assertThat(
              List.of(
                  records.getRow(1).getCell(4).getStringCellValue(),
                  records.getRow(1).getCell(5).getStringCellValue(),
                  records.getRow(1).getCell(6).getStringCellValue(),
                  records.getRow(1).getCell(7).getStringCellValue(),
                  records.getRow(1).getCell(8).getStringCellValue(),
                  records.getRow(1).getCell(9).getStringCellValue(),
                  records.getRow(1).getCell(10).getStringCellValue(),
                  records.getRow(1).getCell(11).getStringCellValue(),
                  records.getRow(1).getCell(12).getStringCellValue()))
          .containsExactly(
              "示例填写内容",
              "示例填写内容",
              "1",
              "2026-01-01",
              "2026-01-01T09:00:00+08:00",
              "示例填写内容",
              "选项一,选项二",
              "示例填写内容",
              "true");
    }
  }

  @Test
  void rejectsTemplateVersionOutsideRequestedTemplate() {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-2"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.generate("tenant-1", "template-1", "version-2"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("template version");
    verifyNoInteractions(fields);
  }

  @Test
  void addsTenantDictionaryDropdownsForSelectAndMultiSelectColumns() throws Exception {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-1"))
        .thenReturn(Optional.of(WorkRecordFixtures.version("version-1")));
    when(fields.listEnabledByVersion("tenant-1", "version-1"))
        .thenReturn(
            List.of(
                dictionaryField("priority", "优先级", FieldType.SELECT, "record_priority", 10),
                dictionaryField(
                    "participants", "参与角色", FieldType.MULTI_SELECT, "record_role", 20)));
    when(dictionaries.enabledItemValues("tenant-1", "record_priority"))
        .thenReturn(List.of("P1", "P2"));
    when(dictionaries.enabledItemValues("tenant-1", "record_role"))
        .thenReturn(List.of("owner", "reviewer"));

    var template = service.generate("tenant-1", "template-1", "version-1");

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template.content()))) {
      var records = workbook.getSheet("records");
      assertThat(records.getDataValidations()).hasSize(2);
      assertValidation(records.getDataValidations().get(0), "dict_values_1", 4, true);
      assertValidation(records.getDataValidations().get(1), "dict_values_2", 5, false);

      var dictionarySheet = workbook.getSheet("字典选项");
      assertThat(dictionarySheet).isNotNull();
      assertThat(workbook.isSheetHidden(workbook.getSheetIndex(dictionarySheet))).isTrue();
      assertThat(dictionarySheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("P1");
      assertThat(dictionarySheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("P2");
      assertThat(dictionarySheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("owner");
      assertThat(dictionarySheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("reviewer");
      assertThat(workbook.getName("dict_values_1").getRefersToFormula())
          .isEqualTo("'字典选项'!$A$2:$A$3");
      assertThat(workbook.getName("dict_values_2").getRefersToFormula())
          .isEqualTo("'字典选项'!$B$2:$B$3");
    }

    verify(dictionaries).enabledItemValues("tenant-1", "record_priority");
    verify(dictionaries).enabledItemValues("tenant-1", "record_role");
  }

  @Test
  void skipsDropdownWhenDictionaryHasNoEnabledItems() throws Exception {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-1"))
        .thenReturn(Optional.of(WorkRecordFixtures.version("version-1")));
    when(fields.listEnabledByVersion("tenant-1", "version-1"))
        .thenReturn(
            List.of(dictionaryField("priority", "优先级", FieldType.SELECT, "empty_dict", 10)));
    when(dictionaries.enabledItemValues("tenant-1", "empty_dict")).thenReturn(List.of());

    var template = service.generate("tenant-1", "template-1", "version-1");

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template.content()))) {
      assertThat(workbook.getSheet("records").getDataValidations()).isEmpty();
      assertThat(workbook.getSheet("字典选项")).isNull();
    }
  }

  private static void assertValidation(
      org.apache.poi.ss.usermodel.DataValidation validation,
      String rangeName,
      int columnIndex,
      boolean showErrorBox) {
    assertThat(validation.getValidationConstraint().getFormula1()).isEqualTo(rangeName);
    assertThat(validation.getRegions().getCellRangeAddresses())
        .containsExactly(
            new CellRangeAddress(1, ExcelImportParser.MAX_ROWS, columnIndex, columnIndex));
    assertThat(validation.getEmptyCellAllowed()).isTrue();
    assertThat(validation.getShowErrorBox()).isEqualTo(showErrorBox);
  }

  private static WorkRecordField dictionaryField(
      String code, String name, FieldType type, String dictCode, int sortOrder) {
    return new WorkRecordField(
        "field-" + code,
        "tenant-1",
        "template-1",
        "version-1",
        name,
        code,
        type,
        false,
        null,
        OptionSource.DICT,
        dictCode,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        sortOrder,
        true,
        WorkRecordFixtures.NOW,
        WorkRecordFixtures.NOW);
  }

  private static WorkRecordField field(
      String code, String name, FieldType type, boolean required, int sortOrder) {
    return new WorkRecordField(
        "field-" + code,
        "tenant-1",
        "template-1",
        "version-1",
        name,
        code,
        type,
        required,
        null,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        sortOrder,
        true,
        WorkRecordFixtures.NOW,
        WorkRecordFixtures.NOW);
  }
}
