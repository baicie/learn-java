package io.aegisops.workrecord.infrastructure.storage;

import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.object-storage", name = "enabled", havingValue = "true")
public class MinioObjectStorageUrlSigner implements ObjectStorageUrlSigner {
  private static final Duration MAX_EXPIRY = Duration.ofDays(7);

  private final ObjectStorageProperties properties;
  private final MinioClient client;

  public MinioObjectStorageUrlSigner(ObjectStorageProperties properties) {
    this.properties = properties;
    this.client =
        MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .region(properties.region())
            .build();
  }

  @Override
  public String presignedPut(String objectKey, String contentType, Duration duration) {
    if (contentType == null || contentType.isBlank() || contentType.length() > 255) {
      throw new IllegalArgumentException("contentType is required");
    }
    return sign(Method.PUT, objectKey, duration);
  }

  @Override
  public String presignedGet(String objectKey, Duration duration) {
    return sign(Method.GET, objectKey, duration);
  }

  private String sign(Method method, String objectKey, Duration duration) {
    ObjectKeyPolicy.requireValid(objectKey);
    if (duration == null
        || duration.isZero()
        || duration.isNegative()
        || duration.compareTo(MAX_EXPIRY) > 0) {
      throw new IllegalArgumentException("signed URL expiry must be between 1 second and 7 days");
    }
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(method)
              .bucket(properties.bucket())
              .object(objectKey)
              .expiry(Math.toIntExact(duration.toSeconds()))
              .build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to create signed object URL", ex);
    }
  }
}
