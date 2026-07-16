package io.aegisops.asset.infrastructure.adapter;

import io.aegisops.asset.api.dto.AssetImportRowResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class AssetImportErrorCsvWriter {
  public byte[] write(List<AssetImportRowResponse> rows) {
    StringWriter output = new StringWriter();
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader(
                "row_number", "external_id", "status", "action", "target_asset_id", "error_codes")
            .build();
    try (CSVPrinter printer = new CSVPrinter(output, format)) {
      for (AssetImportRowResponse row : rows) {
        printer.printRecord(
            row.rowNumber(),
            row.externalId(),
            row.validationStatus(),
            row.resolutionAction(),
            row.resolvedAssetId(),
            String.join("|", row.errorCodes()));
      }
    } catch (IOException exception) {
      throw new IllegalStateException("导入错误 CSV 无法生成", exception);
    }
    return output.toString().getBytes(StandardCharsets.UTF_8);
  }
}
