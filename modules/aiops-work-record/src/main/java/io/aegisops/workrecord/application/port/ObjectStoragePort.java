package io.aegisops.workrecord.application.port;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStoragePort {
  StoredObject put(PutObjectCommand command, InputStream input);

  StoredObject putUnknownLength(
      String objectKey, String contentType, InputStream input, long maxBytes);

  InputStream get(String objectKey);

  InputStream get(String objectKey, long maxBytes);

  StoredObject stat(String objectKey);

  String presignedGet(String objectKey, Duration duration);

  void delete(String objectKey);

  record PutObjectCommand(String objectKey, String contentType, long sizeBytes) {}

  record StoredObject(String objectKey, long sizeBytes, String sha256) {}
}
