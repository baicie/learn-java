package io.aegisops.workrecord.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.work-record")
public class WorkRecordProductionProperties {

  private final Payload payload = new Payload();

  private final Query query = new Query();

  private final Export export = new Export();

  private final Metrics metrics = new Metrics();

  public Payload getPayload() {
    return payload;
  }

  public Query getQuery() {
    return query;
  }

  public Export getExport() {
    return export;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public static WorkRecordProductionProperties defaults() {
    return new WorkRecordProductionProperties();
  }

  public static final class Payload {
    private int schemaMaxBytes = 1_048_576;
    private int designerMaxBytes = 1_048_576;
    private int fieldIndexMaxBytes = 1_048_576;
    private int customDataMaxBytes = 262_144;
    private int builtinDataMaxBytes = 131_072;

    public int getSchemaMaxBytes() {
      return schemaMaxBytes;
    }

    public void setSchemaMaxBytes(int schemaMaxBytes) {
      this.schemaMaxBytes = positive(schemaMaxBytes, "schemaMaxBytes");
    }

    public int getDesignerMaxBytes() {
      return designerMaxBytes;
    }

    public void setDesignerMaxBytes(int designerMaxBytes) {
      this.designerMaxBytes = positive(designerMaxBytes, "designerMaxBytes");
    }

    public int getFieldIndexMaxBytes() {
      return fieldIndexMaxBytes;
    }

    public void setFieldIndexMaxBytes(int fieldIndexMaxBytes) {
      this.fieldIndexMaxBytes = positive(fieldIndexMaxBytes, "fieldIndexMaxBytes");
    }

    public int getCustomDataMaxBytes() {
      return customDataMaxBytes;
    }

    public void setCustomDataMaxBytes(int customDataMaxBytes) {
      this.customDataMaxBytes = positive(customDataMaxBytes, "customDataMaxBytes");
    }

    public int getBuiltinDataMaxBytes() {
      return builtinDataMaxBytes;
    }

    public void setBuiltinDataMaxBytes(int builtinDataMaxBytes) {
      this.builtinDataMaxBytes = positive(builtinDataMaxBytes, "builtinDataMaxBytes");
    }
  }

  public static final class Query {
    private int maxPageSize = 200;
    private long maxOffset = 100_000;
    private long slowThresholdMillis = 500;

    public int getMaxPageSize() {
      return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
      this.maxPageSize = positive(maxPageSize, "maxPageSize");
    }

    public long getMaxOffset() {
      return maxOffset;
    }

    public void setMaxOffset(long maxOffset) {
      if (maxOffset < 0) {
        throw new IllegalArgumentException("maxOffset must not be negative");
      }
      this.maxOffset = maxOffset;
    }

    public long getSlowThresholdMillis() {
      return slowThresholdMillis;
    }

    public void setSlowThresholdMillis(long slowThresholdMillis) {
      if (slowThresholdMillis < 1) {
        throw new IllegalArgumentException("slowThresholdMillis must be positive");
      }
      this.slowThresholdMillis = slowThresholdMillis;
    }
  }

  public static final class Export {
    private int maxRows = 5000;
    private int requestsPerMinute = 5;
    private long leaseSeconds = 120;

    public int getMaxRows() {
      return maxRows;
    }

    public void setMaxRows(int maxRows) {
      if (maxRows < 1 || maxRows > 100_000) {
        throw new IllegalArgumentException("maxRows must be between 1 and 100000");
      }
      this.maxRows = maxRows;
    }

    public int getRequestsPerMinute() {
      return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
      this.requestsPerMinute = positive(requestsPerMinute, "requestsPerMinute");
    }

    public long getLeaseSeconds() {
      return leaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
      if (leaseSeconds < 10) {
        throw new IllegalArgumentException("leaseSeconds must be at least 10");
      }
      this.leaseSeconds = leaseSeconds;
    }
  }

  public static final class Metrics {
    private long snapshotDelayMs = 60_000;

    public long getSnapshotDelayMs() {
      return snapshotDelayMs;
    }

    public void setSnapshotDelayMs(long snapshotDelayMs) {
      if (snapshotDelayMs < 10_000) {
        throw new IllegalArgumentException("snapshotDelayMs must be at least 10000");
      }
      this.snapshotDelayMs = snapshotDelayMs;
    }
  }

  private static int positive(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}