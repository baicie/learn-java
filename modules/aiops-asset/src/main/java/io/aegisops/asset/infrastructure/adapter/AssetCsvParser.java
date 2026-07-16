package io.aegisops.asset.infrastructure.adapter;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

@Component
public class AssetCsvParser {
  public static final int MAX_BYTES = 5 * 1024 * 1024;
  public static final int MAX_ROWS = 5000;
  public static final List<String> TEMPLATE_HEADERS =
      List.of(
          "external_id",
          "asset_type",
          "name",
          "display_name",
          "environment",
          "site",
          "owner_team",
          "criticality",
          "ip",
          "machine_id",
          "cloud_instance_id",
          "k8s_uid",
          "tags");
  private static final Set<String> REQUIRED_HEADERS = Set.of("external_id", "asset_type", "name");

  public List<AssetCsvRow> parse(byte[] content) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("CSV 文件不能为空");
    }
    if (content.length > MAX_BYTES) {
      throw new IllegalArgumentException("CSV 文件不能超过 5 MiB");
    }

    String text = decodeUtf8(content);
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .build();
    try (CSVParser csv = new CSVParser(new StringReader(text), format)) {
      validateHeaders(csv.getHeaderNames());
      List<AssetCsvRow> rows = csv.stream().map(this::toRow).toList();
      if (rows.size() > MAX_ROWS) {
        throw new IllegalArgumentException("CSV 数据行不能超过 5000 行");
      }
      return rows;
    } catch (IOException exception) {
      throw new IllegalArgumentException("CSV 文件读取失败", exception);
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null
          && exception.getMessage().toLowerCase().contains("duplicate")) {
        throw new IllegalArgumentException("CSV 存在重复表头", exception);
      }
      throw exception;
    }
  }

  private void validateHeaders(List<String> headers) {
    for (String required : REQUIRED_HEADERS) {
      if (!headers.contains(required)) {
        throw new IllegalArgumentException("CSV 缺少必填列: " + required);
      }
    }
    List<String> unknown = headers.stream().filter(header -> !TEMPLATE_HEADERS.contains(header)).toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("CSV 包含 unknown columns: " + unknown);
    }
  }

  private AssetCsvRow toRow(CSVRecord row) {
    return new AssetCsvRow(
        Math.toIntExact(row.getRecordNumber() + 1),
        value(row, "external_id"),
        value(row, "asset_type"),
        value(row, "name"),
        value(row, "display_name"),
        value(row, "environment"),
        value(row, "site"),
        value(row, "owner_team"),
        value(row, "criticality"),
        value(row, "ip"),
        value(row, "machine_id"),
        value(row, "cloud_instance_id"),
        value(row, "k8s_uid"),
        parseTags(value(row, "tags")));
  }

  private String value(CSVRecord row, String header) {
    return row.isMapped(header) ? row.get(header).trim() : "";
  }

  private Map<String, String> parseTags(String value) {
    if (value.isBlank()) {
      return Map.of();
    }
    Map<String, String> tags = new LinkedHashMap<>();
    for (String token : value.split(";")) {
      int separator = token.indexOf('=');
      if (separator <= 0) {
        throw new IllegalArgumentException("tags 必须使用 key=value;key=value 格式");
      }
      tags.put(token.substring(0, separator).trim(), token.substring(separator + 1).trim());
    }
    return Map.copyOf(tags);
  }

  private String decodeUtf8(byte[] content) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content))
          .toString()
          .replaceFirst("^\\uFEFF", "");
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("CSV 必须使用 UTF-8 编码", exception);
    }
  }
}
