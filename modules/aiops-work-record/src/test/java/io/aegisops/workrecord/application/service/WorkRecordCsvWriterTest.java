package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkRecordCsvWriterTest {
  private final WorkRecordCsvWriter writer = new WorkRecordCsvWriter();

  @Test
  void shouldWriteUtf8BomAndRfc4180QuotedCsv() {
    byte[] result = writer.write(List.of("标题", "备注"), List.of(List.of("日报", "包含,逗号和\"引号\"")));

    String csv = new String(result, StandardCharsets.UTF_8);

    assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
    assertThat(csv).contains("\"标题\",\"备注\"\r\n").contains("\"日报\",\"包含,逗号和\"\"引号\"\"\"\r\n");
  }

  @Test
  void shouldProtectFormulaInjection() {
    byte[] result =
        writer.write(
            List.of("a", "b", "c", "d"), List.of(List.of("=1+1", "+cmd", "-10", "@SUM(A1:A2)")));

    String csv = new String(result, StandardCharsets.UTF_8);

    assertThat(csv)
        .contains("\"'=1+1\"")
        .contains("\"'+cmd\"")
        .contains("\"'-10\"")
        .contains("\"'@SUM(A1:A2)\"");
  }

  @Test
  void shouldProtectFormulaAfterLeadingSpaces() {
    byte[] result = writer.write(List.of("value"), List.of(List.of("   =1+1")));

    String csv = new String(result, StandardCharsets.UTF_8);

    assertThat(csv).contains("\"'   =1+1\"");
  }

  @Test
  void shouldRejectMismatchedRowSize() {
    assertThatThrownBy(() -> writer.write(List.of("a", "b"), List.of(List.of("only-one"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("row size does not match");
  }

  @Test
  void streamsHeaderAndRowsWithoutBufferingWholeFile() throws Exception {
    StringWriter output = new StringWriter();

    writer.writeHeader(output, List.of("标题", "值"));
    writer.writeRow(output, List.of("日报", "=1+1"), 2);

    assertThat(output.toString())
        .startsWith("\uFEFF")
        .contains("\"标题\",\"值\"\r\n")
        .contains("\"日报\",\"'=1+1\"\r\n");
  }
}
