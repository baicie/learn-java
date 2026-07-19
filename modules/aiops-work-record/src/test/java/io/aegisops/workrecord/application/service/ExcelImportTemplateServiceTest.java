package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.ResourceNotFoundException;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.support.WorkRecordFixtures;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelImportTemplateServiceTest {
  private final WorkRecordTemplateVersionRepository versions =
      mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fields = mock(WorkRecordFieldIndexRepository.class);
  private final ExcelImportTemplateService service =
      new ExcelImportTemplateService(versions, fields);

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
  void rejectsTemplateVersionOutsideRequestedTemplate() {
    when(versions.findByTemplateAndVersion("tenant-1", "template-1", "version-2"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.generate("tenant-1", "template-1", "version-2"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("template version");
    verifyNoInteractions(fields);
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
