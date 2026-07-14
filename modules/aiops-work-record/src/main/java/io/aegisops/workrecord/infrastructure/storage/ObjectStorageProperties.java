package io.aegisops.workrecord.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.object-storage")
public record ObjectStorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    long attachmentMaxBytes,
    int downloadUrlExpirySeconds,
    String region) {

  public ObjectStorageProperties {
    endpoint = fallback(endpoint, "http://localhost:9002");
    accessKey = fallback(accessKey, "minioadmin");
    secretKey = fallback(secretKey, "minioadmin");
    bucket = fallback(bucket, "aegisops-work-record");
    region = fallback(region, "us-east-1");
    attachmentMaxBytes = attachmentMaxBytes <= 0 ? 20L * 1024L * 1024L : attachmentMaxBytes;
    downloadUrlExpirySeconds = downloadUrlExpirySeconds <= 0 ? 300 : downloadUrlExpirySeconds;
  }

  private static String fallback(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }
}
