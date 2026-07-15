package io.aegisops.workrecord.application.port;

import java.time.Duration;

public interface ObjectStorageUrlSigner {
  String presignedPut(String objectKey, String contentType, Duration duration);

  String presignedGet(String objectKey, Duration duration);
}
