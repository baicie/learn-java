package io.aegisops.platform.calendar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Parses the compact holiday exception template used by the work calendar. */
public class CalendarXlsxImporter {
  private static final List<String> HEADERS = List.of("date", "holidayName", "remark");
  private static final int MAX_ROWS = 1000;
  private static final int MAX_HOLIDAY_NAME_LENGTH = 128;

  public List<CalendarImportRow> parse(byte[] content) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("xlsx file is required");
    }

    try (var workbook = openWorkbook(content)) {
      Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
      if (sheet == null) {
        throw new IllegalArgumentException("xlsx file has no worksheet");
      }
      validateHeaders(sheet.getRow(0));

      var formatter = new DataFormatter();
      var rows = new java.util.ArrayList<CalendarImportRow>();
      Set<LocalDate> dates = new HashSet<>();

      for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        Row row = sheet.getRow(rowIndex);
        validateNoExtraColumns(row, rowIndex + 1, formatter);
        if (isEmpty(row, formatter)) {
          continue;
        }
        if (rows.size() >= MAX_ROWS) {
          throw new IllegalArgumentException("holiday import exceeds " + MAX_ROWS + " rows");
        }

        LocalDate date = readDate(row, rowIndex + 1, formatter);
        if (!dates.add(date)) {
          throw new IllegalArgumentException("duplicate date at xlsx row " + (rowIndex + 1));
        }

        String holidayName = optionalText(row, 1, formatter);
        if (holidayName == null) {
          throw new IllegalArgumentException(
              "holidayName is required at xlsx row " + (rowIndex + 1));
        }
        if (holidayName.length() > MAX_HOLIDAY_NAME_LENGTH) {
          throw new IllegalArgumentException(
              "holidayName must not exceed "
                  + MAX_HOLIDAY_NAME_LENGTH
                  + " characters at xlsx row "
                  + (rowIndex + 1));
        }
        rows.add(new CalendarImportRow(date, holidayName, optionalText(row, 2, formatter)));
      }

      if (rows.isEmpty()) {
        throw new IllegalArgumentException("xlsx file has no data rows");
      }
      return rows;
    } catch (IOException ex) {
      throw new IllegalArgumentException("invalid xlsx file", ex);
    }
  }

  public byte[] createTemplate(int year) {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Holiday exceptions");
      CellStyle headerStyle = createHeaderStyle(workbook);
      CellStyle dateStyle = createDateStyle(workbook);
      CellStyle wrappedStyle = createWrappedStyle(workbook);
      Row header = sheet.createRow(0);
      header.setHeightInPoints(24);
      for (int index = 0; index < HEADERS.size(); index++) {
        Cell cell = header.createCell(index);
        cell.setCellValue(HEADERS.get(index));
        cell.setCellStyle(headerStyle);
      }
      sheet.setColumnWidth(0, 14 * 256);
      sheet.setColumnWidth(1, 24 * 256);
      sheet.setColumnWidth(2, 48 * 256);

      Row holiday = sheet.createRow(1);
      holiday.setHeightInPoints(32);
      Cell date = holiday.createCell(0);
      date.setCellValue(LocalDate.of(year, 1, 1));
      date.setCellStyle(dateStyle);
      holiday.createCell(1).setCellValue("元旦");
      Cell remark = holiday.createCell(2);
      remark.setCellValue("示例：仅填写法定节假日，可替换或保留此行");
      remark.setCellStyle(wrappedStyle);

      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, HEADERS.size() - 1));
      createInstructionsSheet(workbook, year, headerStyle, wrappedStyle);
      workbook.setActiveSheet(0);
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx template", ex);
    }
  }

  private void validateHeaders(Row header) {
    if (header == null) {
      throw new IllegalArgumentException("xlsx header is required");
    }
    var formatter = new DataFormatter();
    for (int index = 0; index < HEADERS.size(); index++) {
      String actual = formatter.formatCellValue(header.getCell(index)).trim();
      if (!HEADERS.get(index).equals(actual)) {
        throw new IllegalArgumentException("invalid xlsx header: expected " + HEADERS.get(index));
      }
    }
    validateNoExtraColumns(header, 1, formatter);
  }

  private XSSFWorkbook openWorkbook(byte[] content) {
    try {
      return new XSSFWorkbook(new ByteArrayInputStream(content));
    } catch (IOException | RuntimeException ex) {
      throw new IllegalArgumentException("invalid xlsx file", ex);
    }
  }

  private void validateNoExtraColumns(Row row, int rowNumber, DataFormatter formatter) {
    if (row == null) {
      return;
    }
    for (int column = HEADERS.size(); column < row.getLastCellNum(); column++) {
      if (optionalText(row, column, formatter) != null) {
        throw new IllegalArgumentException("unexpected column at xlsx row " + rowNumber);
      }
    }
  }

  private LocalDate readDate(Row row, int rowNumber, DataFormatter formatter) {
    Cell cell = row.getCell(0);
    if (cell == null || cell.getCellType() == CellType.BLANK) {
      throw new IllegalArgumentException("date is required at xlsx row " + rowNumber);
    }
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().toLocalDate();
    }
    try {
      return LocalDate.parse(formatter.formatCellValue(cell).trim());
    } catch (java.time.format.DateTimeParseException ex) {
      throw new IllegalArgumentException("invalid date at xlsx row " + rowNumber, ex);
    }
  }

  private String optionalText(Row row, int column, DataFormatter formatter) {
    if (row == null || row.getCell(column) == null) {
      return null;
    }
    String value = formatter.formatCellValue(row.getCell(column)).trim();
    return value.isEmpty() ? null : value;
  }

  private boolean isEmpty(Row row, DataFormatter formatter) {
    return row == null
        || IntStream.range(0, HEADERS.size())
            .allMatch(index -> optionalText(row, index, formatter) == null);
  }

  private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
    Font font = workbook.createFont();
    font.setBold(true);
    font.setColor(IndexedColors.WHITE.getIndex());

    CellStyle style = workbook.createCellStyle();
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.LEFT);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBottomBorderColor(IndexedColors.DARK_TEAL.getIndex());
    return style;
  }

  private CellStyle createDateStyle(XSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  private CellStyle createWrappedStyle(XSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setWrapText(true);
    style.setVerticalAlignment(VerticalAlignment.TOP);
    return style;
  }

  private void createInstructionsSheet(
      XSSFWorkbook workbook, int year, CellStyle headerStyle, CellStyle wrappedStyle) {
    Sheet instructions = workbook.createSheet("Instructions");
    instructions.setDisplayGridlines(false);
    instructions.setColumnWidth(0, 22 * 256);
    instructions.setColumnWidth(1, 72 * 256);

    Row title = instructions.createRow(0);
    title.setHeightInPoints(28);
    for (int column = 0; column < 2; column++) {
      Cell cell = title.createCell(column);
      cell.setCellStyle(headerStyle);
    }
    title.getCell(0).setCellValue("工作日历法定节假日导入说明 / Statutory Holiday Import");

    addInstruction(instructions, 2, "日历年度", String.valueOf(year), wrappedStyle);
    addInstruction(
        instructions, 3, "固定表头", "date（YYYY-MM-DD）、holidayName（必填）、remark（选填）", wrappedStyle);
    addInstruction(instructions, 4, "允许填写", "仅填写所选年度的法定节假日", wrappedStyle);
    addInstruction(instructions, 5, "不要填写", "普通工作日、周末或调休工作日", wrappedStyle);
    addInstruction(
        instructions, 6, "导入限制", "请勿修改表头；日期不可重复；holidayName 最多 128 个字符；最多 1000 行", wrappedStyle);
  }

  private void addInstruction(
      Sheet sheet, int rowIndex, String label, String value, CellStyle wrappedStyle) {
    Row row = sheet.createRow(rowIndex);
    row.setHeightInPoints(30);
    Cell labelCell = row.createCell(0);
    labelCell.setCellValue(label);
    Font font = sheet.getWorkbook().createFont();
    font.setBold(true);
    CellStyle labelStyle = sheet.getWorkbook().createCellStyle();
    labelStyle.setFont(font);
    labelStyle.setVerticalAlignment(VerticalAlignment.TOP);
    labelCell.setCellStyle(labelStyle);
    Cell valueCell = row.createCell(1);
    valueCell.setCellValue(value);
    valueCell.setCellStyle(wrappedStyle);
  }

  public record CalendarImportRow(LocalDate date, String holidayName, String remark) {}
}
