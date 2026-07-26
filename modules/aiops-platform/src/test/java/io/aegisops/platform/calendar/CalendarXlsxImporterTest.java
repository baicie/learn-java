package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class CalendarXlsxImporterTest {

  private final CalendarXlsxImporter importer = new CalendarXlsxImporter();

  @Test
  void shouldParseHolidayExceptionRows() {
    var rows =
        importer.parse(
            xlsx(
                new XlsxRow("2026-01-01", "New Year's Day", null),
                new XlsxRow("2026-10-01", "National Day", "Statutory public holiday")));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(rows.get(0).holidayName()).isEqualTo("New Year's Day");
    assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(rows.get(1).holidayName()).isEqualTo("National Day");
    assertThat(rows.get(1).remark()).isEqualTo("Statutory public holiday");
  }

  @Test
  void shouldParseExcelDateCell() {
    var rows = importer.parse(xlsxWithExcelDateCell());

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(rows.get(0).holidayName()).isEqualTo("National Day");
  }

  @Test
  void shouldCreateImportableHolidayTemplate() throws IOException {
    byte[] template = importer.createTemplate(2026);

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
      Sheet sheet = workbook.getSheet("Holiday exceptions");
      assertThat(sheet).isNotNull();
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("date");
      assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("holidayName");
      assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("remark");
      Cell exampleDate = sheet.getRow(1).getCell(0);
      assertThat(exampleDate.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(exampleDate.getLocalDateTimeCellValue().toLocalDate())
          .isEqualTo(LocalDate.of(2026, 1, 1));

      Sheet instructions = workbook.getSheet("Instructions");
      assertThat(instructions).isNotNull();
      assertThat(instructions.getRow(0).getCell(0).getStringCellValue())
          .startsWith("工作日历法定节假日导入说明");
      assertThat(instructions.getRow(2).getCell(1).getStringCellValue()).isEqualTo("2026");
      assertThat(instructions.getRow(4).getCell(1).getStringCellValue()).isEqualTo("仅填写所选年度的法定节假日");
      assertThat(instructions.getRow(5).getCell(1).getStringCellValue())
          .contains("普通工作日", "周末", "调休工作日");
    }

    assertThat(importer.parse(template))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.date()).isEqualTo(LocalDate.of(2026, 1, 1));
              assertThat(row.holidayName()).isEqualTo("元旦");
            });
  }

  @Test
  void shouldRejectLegacyDayTypeColumn() {
    assertThatThrownBy(() -> importer.parse(xlsxWithLegacyDayTypeColumn()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected holidayName");
  }

  @Test
  void shouldRejectHolidayWithoutName() {
    assertThatThrownBy(() -> importer.parse(xlsx(new XlsxRow("2026-10-01", null, null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holidayName is required");
  }

  @Test
  void shouldRejectHolidayNameLongerThanOneHundredTwentyEightCharacters() {
    assertThatThrownBy(() -> importer.parse(xlsx(new XlsxRow("2026-10-01", "H".repeat(129), null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("holidayName must not exceed 128 characters");
  }

  @Test
  void shouldRejectHolidayWithoutDate() {
    assertThatThrownBy(() -> importer.parse(xlsx(new XlsxRow(null, "National Day", null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date is required");
  }

  @Test
  void shouldRejectDuplicateExceptionDates() {
    assertThatThrownBy(
            () ->
                importer.parse(
                    xlsx(
                        new XlsxRow("2026-10-01", "National Day", null),
                        new XlsxRow("2026-10-01", "Duplicate holiday", "Conflicting row"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate date");
  }

  @Test
  void shouldRejectWorkbookWithoutDataRows() {
    assertThatThrownBy(() -> importer.parse(xlsx()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no data rows");
  }

  @Test
  void shouldRejectMoreThanOneThousandHolidayRows() {
    XlsxRow[] rows =
        IntStream.rangeClosed(0, 1000)
            .mapToObj(
                offset ->
                    new XlsxRow(
                        LocalDate.of(2020, 1, 1).plusDays(offset).toString(),
                        "Holiday " + offset,
                        null))
            .toArray(XlsxRow[]::new);

    assertThatThrownBy(() -> importer.parse(xlsx(rows)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds 1000 rows");
  }

  @Test
  void shouldRejectNonEmptyUnexpectedColumn() {
    assertThatThrownBy(() -> importer.parse(xlsxWithUnexpectedColumn()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unexpected column at xlsx row 2");
  }

  @Test
  void shouldReportCorruptWorkbookAsInvalidXlsx() {
    assertThatThrownBy(() -> importer.parse(new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid xlsx file");
  }

  private byte[] xlsx(XlsxRow... rows) {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = createSheet(workbook);
      for (int index = 0; index < rows.length; index++) {
        XlsxRow source = rows[index];
        Row row = sheet.createRow(index + 1);
        if (source.date() != null) {
          row.createCell(0).setCellValue(source.date());
        }
        if (source.holidayName() != null) {
          row.createCell(1).setCellValue(source.holidayName());
        }
        if (source.remark() != null) {
          row.createCell(2).setCellValue(source.remark());
        }
      }
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx test data", ex);
    }
  }

  private byte[] xlsxWithExcelDateCell() {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = createSheet(workbook);
      Row row = sheet.createRow(1);
      Cell date = row.createCell(0);
      date.setCellValue(LocalDate.of(2026, 10, 1));
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
      date.setCellStyle(dateStyle);
      row.createCell(1).setCellValue("National Day");
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx test data", ex);
    }
  }

  private byte[] xlsxWithLegacyDayTypeColumn() {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Holiday exceptions");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("date");
      header.createCell(1).setCellValue("dayType");
      header.createCell(2).setCellValue("holidayName");
      header.createCell(3).setCellValue("remark");
      Row row = sheet.createRow(1);
      row.createCell(0).setCellValue("2026-01-02");
      row.createCell(1).setCellValue("WORKDAY");
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx test data", ex);
    }
  }

  private byte[] xlsxWithUnexpectedColumn() {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = createSheet(workbook);
      Row row = sheet.createRow(1);
      row.createCell(0).setCellValue("2026-10-01");
      row.createCell(1).setCellValue("National Day");
      row.createCell(3).setCellValue("ADJUSTED_WORKDAY");
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx test data", ex);
    }
  }

  private Sheet createSheet(XSSFWorkbook workbook) {
    Sheet sheet = workbook.createSheet("Holiday exceptions");
    Row header = sheet.createRow(0);
    header.createCell(0).setCellValue("date");
    header.createCell(1).setCellValue("holidayName");
    header.createCell(2).setCellValue("remark");
    return sheet;
  }

  private record XlsxRow(String date, String holidayName, String remark) {}
}
