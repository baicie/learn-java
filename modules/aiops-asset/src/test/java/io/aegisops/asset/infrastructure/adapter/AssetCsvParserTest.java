package io.aegisops.asset.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AssetCsvParserTest {
  private final AssetCsvParser parser = new AssetCsvParser();

  @Test
  void parsesUtf8QuotedCommaAndTagsWithoutExecutingFormulaText() {
    String csv =
        "external_id,asset_type,name,display_name,tags\n"
            + "host-1,host,db-1,\"生产,数据库\",\"role=mysql;note==1+1\"\n";

    var rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().displayName()).isEqualTo("生产,数据库");
    assertThat(rows.getFirst().tags()).containsEntry("note", "=1+1");
  }

  @Test
  void rejectsUnknownAndDuplicateHeaders() {
    assertThatThrownBy(
            () ->
                parser.parse(
                    "external_id,asset_type,name,unknown\na,host,a,x\n"
                        .getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");

    assertThatThrownBy(
            () ->
                parser.parse(
                    "external_id,asset_type,name,name\na,host,a,b\n"
                        .getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("重复");
  }

  @Test
  void enforcesRequiredHeadersAndMaximumRows() {
    assertThatThrownBy(
            () -> parser.parse("asset_type,name\nhost,a\n".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("external_id");

    StringBuilder csv = new StringBuilder("external_id,asset_type,name\n");
    for (int i = 0; i < 5001; i++) {
      csv.append("host-").append(i).append(",host,node-").append(i).append('\n');
    }
    assertThatThrownBy(() -> parser.parse(csv.toString().getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5000");
  }
}
