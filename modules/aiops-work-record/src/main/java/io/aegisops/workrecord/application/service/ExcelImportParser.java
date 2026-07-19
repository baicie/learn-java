package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.model.ImportedRecordRow;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelImportParser {
  static final int MAX_ROWS = 20_000;
  static final int MAX_COLUMNS = 300;
  private static final Pattern READABLE_HEADER =
      Pattern.compile(".*\\[([A-Za-z][A-Za-z0-9_]*)]\\s*$");

  private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

  public List<ImportedRecordRow> parse(
      InputStream input, List<WorkRecordField> fields, ImportDefaults defaults) {
    ParseResult result = parseAll(input, fields, defaults);
    if (!result.failures().isEmpty()) {
      ParseFailure failure = result.failures().getFirst();
      throw new ImportRowException(failure.rowNumber(), failure.fieldCode(), failure.message());
    }
    return result.rows();
  }

  public ParseResult parseAll(
      InputStream input, List<WorkRecordField> fields, ImportDefaults defaults) {
    if (input == null) {
      throw new IllegalArgumentException("Excel input is required");
    }
    if (defaults == null) {
      throw new IllegalArgumentException("import defaults are required");
    }
    try (Workbook workbook = new XSSFWorkbook(input)) {
      if (workbook.getNumberOfSheets() == 0) {
        throw new IllegalArgumentException("Excel workbook has no sheet");
      }
      var sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(sheet.getFirstRowNum());
      if (header == null) {
        throw new IllegalArgumentException("Excel header row is required");
      }
      Map<Integer, ColumnBinding> bindings = bindHeader(header, fields);
      if (sheet.getLastRowNum() - header.getRowNum() > MAX_ROWS) {
        throw new IllegalArgumentException("Excel rows exceed " + MAX_ROWS);
      }

      List<ImportedRecordRow> rows = new ArrayList<>();
      List<ParseFailure> failures = new ArrayList<>();
      for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
        Row row = sheet.getRow(index);
        if (row != null && !isBlank(row)) {
          try {
            rows.add(parseRow(row, bindings, defaults));
          } catch (ImportRowException ex) {
            failures.add(
                new ParseFailure(ex.rowNumber(), ex.fieldCode(), safeMessage(ex.getMessage())));
          }
        }
      }
      return new ParseResult(List.copyOf(rows), List.copyOf(failures));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("failed to parse Excel import", ex);
    }
  }

  private Map<Integer, ColumnBinding> bindHeader(Row header, List<WorkRecordField> fields) {
    if (header.getLastCellNum() < 1) {
      throw new IllegalArgumentException("Excel header row is required");
    }
    if (header.getLastCellNum() > MAX_COLUMNS) {
      throw new IllegalArgumentException("Excel columns exceed " + MAX_COLUMNS);
    }
    Map<String, WorkRecordField> fieldsByCode = new HashMap<>();
    if (fields != null) {
      for (WorkRecordField field : fields) {
        if (field != null && field.enabled()) {
          fieldsByCode.put(field.fieldCode(), field);
        }
      }
    }

    Map<Integer, ColumnBinding> bindings = new LinkedHashMap<>();
    Set<String> seen = new java.util.HashSet<>();
    for (int index = 0; index < header.getLastCellNum(); index++) {
      String name = headerCode(text(header.getCell(index)).trim());
      if (name.isEmpty()) {
        continue;
      }
      if (!seen.add(name)) {
        throw new IllegalArgumentException("duplicate Excel column: " + name);
      }
      ColumnBinding binding = builtinBinding(name);
      if (binding == null) {
        WorkRecordField field = fieldsByCode.get(name);
        if (field == null) {
          throw new IllegalArgumentException("unknown Excel column: " + name);
        }
        binding = ColumnBinding.custom(field);
      }
      bindings.put(index, binding);
    }
    if (bindings.values().stream().noneMatch(ColumnBinding::title)) {
      throw new IllegalArgumentException("Excel column title is required");
    }
    return Map.copyOf(bindings);
  }

  private ImportedRecordRow parseRow(
      Row row, Map<Integer, ColumnBinding> bindings, ImportDefaults defaults) {
    String title = null;
    String status = defaults.defaultStatus();
    String ownerId = defaults.defaultOwnerId();
    OffsetDateTime recordTime = defaults.defaultRecordTime();
    Map<String, Object> custom = new LinkedHashMap<>();

    try {
      for (var entry : bindings.entrySet()) {
        ColumnBinding binding = entry.getValue();
        Object value = value(row.getCell(entry.getKey()), binding.field());
        if (binding.title()) {
          title = string(value);
        } else if (binding.status()) {
          status = value == null ? status : string(value);
        } else if (binding.owner()) {
          ownerId = value == null ? ownerId : string(value);
        } else if (binding.recordTime()) {
          recordTime = value == null ? recordTime : toOffsetDateTime(value);
        } else if (value != null) {
          custom.put(binding.field().fieldCode(), value);
        }
      }
    } catch (ImportRowException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      throw new ImportRowException(row.getRowNum() + 1, null, ex.getMessage(), ex);
    }

    if (title == null || title.isBlank()) {
      throw new ImportRowException(row.getRowNum() + 1, "title", "title is required");
    }
    if (recordTime == null) {
      throw new ImportRowException(row.getRowNum() + 1, "recordTime", "recordTime is required");
    }
    return new ImportedRecordRow(
        row.getRowNum() + 1, title, status, blankToNull(ownerId), recordTime, custom);
  }

  private Object value(Cell cell, WorkRecordField field) {
    if (cell == null || cell.getCellType() == CellType.BLANK) {
      return null;
    }
    if (cell.getCellType() == CellType.FORMULA) {
      throw new IllegalArgumentException("Excel formulas are not allowed");
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
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC);
    }
    return text(cell).trim();
  }

  private BigDecimal number(Cell cell) {
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
    }
    try {
      return new BigDecimal(text(cell).trim()).stripTrailingZeros();
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid number: " + text(cell), ex);
    }
  }

  private boolean bool(Cell cell) {
    if (cell.getCellType() == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }
    return switch (text(cell).trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "y", "是" -> true;
      case "false", "0", "no", "n", "否" -> false;
      default -> throw new IllegalArgumentException("invalid boolean: " + text(cell));
    };
  }

  private LocalDate date(Cell cell) {
    return cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
        ? cell.getLocalDateTimeCellValue().toLocalDate()
        : LocalDate.parse(text(cell).trim());
  }

  private OffsetDateTime datetime(Cell cell) {
    return cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
        ? cell.getLocalDateTimeCellValue().atOffset(ZoneOffset.UTC)
        : OffsetDateTime.parse(text(cell).trim());
  }

  private static OffsetDateTime toOffsetDateTime(Object value) {
    return value instanceof OffsetDateTime dateTime
        ? dateTime
        : OffsetDateTime.parse(String.valueOf(value));
  }

  private static List<String> multi(String raw) {
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
    for (int index = Math.max(0, row.getFirstCellNum()); index < row.getLastCellNum(); index++) {
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

  private static ColumnBinding builtinBinding(String name) {
    return switch (name) {
      case "title" -> ColumnBinding.titleBinding();
      case "status" -> ColumnBinding.statusBinding();
      case "ownerId" -> ColumnBinding.ownerBinding();
      case "recordTime" -> ColumnBinding.recordTimeBinding();
      default -> null;
    };
  }

  private static String headerCode(String header) {
    var matcher = READABLE_HEADER.matcher(header);
    return matcher.matches() ? matcher.group(1) : header;
  }

  private static String string(Object value) {
    return value == null ? null : String.valueOf(value).trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record ImportDefaults(
      String defaultStatus, String defaultOwnerId, OffsetDateTime defaultRecordTime) {}

  public record ParseResult(List<ImportedRecordRow> rows, List<ParseFailure> failures) {}

  public record ParseFailure(int rowNumber, String fieldCode, String message) {}

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "invalid row";
    }
    String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
    return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
  }

  private record ColumnBinding(
      boolean title, boolean status, boolean owner, boolean recordTime, WorkRecordField field) {
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

    private ImportRowException(
        int rowNumber, String fieldCode, String message, IllegalArgumentException cause) {
      super(message, cause);
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
