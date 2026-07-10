package io.aegisops.workrecord.application.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordCsvWriter {
  private static final char BOM = '\uFEFF';

  public byte[] write(
      List<String> headers,
      List<List<String>> rows) {
    if (headers == null || headers.isEmpty()) {
      throw new IllegalArgumentException(
          "csv headers must not be empty");
    }

    StringBuilder csv = new StringBuilder();
    csv.append(BOM);

    appendRow(csv, headers);

    if (rows != null) {
      for (List<String> row : rows) {
        if (row.size() != headers.size()) {
          throw new IllegalArgumentException(
              "csv row size does not match header size");
        }
        appendRow(csv, row);
      }
    }

    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private void appendRow(
      StringBuilder csv,
      List<String> values) {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        csv.append(',');
      }

      csv.append(quote(protectFormula(values.get(index))));
    }

    csv.append("\r\n");
  }

  private String protectFormula(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    String withoutLeadingWhitespace = value.stripLeading();
    if (withoutLeadingWhitespace.isEmpty()) {
      return value;
    }

    char first = withoutLeadingWhitespace.charAt(0);
    if (first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r') {
      return "'" + value;
    }

    return value;
  }

  private String quote(String value) {
    return "\""
        + value.replace("\"", "\"\"")
        + "\"";
  }
}
