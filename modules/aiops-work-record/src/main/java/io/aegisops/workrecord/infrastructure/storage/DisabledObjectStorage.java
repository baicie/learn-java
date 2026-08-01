package io.aegisops.workrecord.infrastructure.storage;

import io.aegisops.common.exception.AppException;
import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import java.io.InputStream;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "aiops.object-storage",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledObjectStorage implements ObjectStoragePort, ObjectStorageUrlSigner {
  @Override
  public StoredObject put(PutObjectCommand command, InputStream input) {
    throw disabled();
  }

  @Override
  public StoredObject putUnknownLength(
      String objectKey, String contentType, InputStream input, long maxBytes) {
    throw disabled();
  }

  @Override
  public InputStream get(String objectKey) {
    throw disabled();
  }

  @Override
  public InputStream get(String objectKey, long maxBytes) {
    throw disabled();
  }

  @Override
  public StoredObject stat(String objectKey) {
    throw disabled();
  }

  @Override
  public String presignedGet(String objectKey, Duration duration) {
    throw disabled();
  }

  @Override
  public String presignedPut(String objectKey, String contentType, Duration duration) {
    throw disabled();
  }

  @Override
  public void delete(String objectKey) {
    throw disabled();
  }

  private static AppException disabled() {
    return new AppException(
        "OBJECT_STORAGE_DISABLED", 503, "Object storage is disabled for this deployment");
  }
}
