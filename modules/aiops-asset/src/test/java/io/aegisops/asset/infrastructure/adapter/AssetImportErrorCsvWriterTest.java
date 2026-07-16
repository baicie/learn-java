package io.aegisops.asset.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.asset.api.dto.AssetImportRowResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetImportErrorCsvWriterTest {
  @Test
  void writesProblemRowsAsUtf8Csv() {
    var row =
        new AssetImportRowResponse(
            2,
            "host,1",
            "invalid",
            null,
            null,
            Map.of("name", "数据库"),
            List.of("NAME_REQUIRED", "ASSET_TYPE_INVALID"));

    String csv =
        new String(new AssetImportErrorCsvWriter().write(List.of(row)), StandardCharsets.UTF_8);

    assertThat(csv)
        .startsWith("row_number,external_id,status,action,target_asset_id,error_codes")
        .contains("\"host,1\"")
        .contains("NAME_REQUIRED|ASSET_TYPE_INVALID");
  }
}
