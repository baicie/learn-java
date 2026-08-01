package io.aegisops.workrecord.infrastructure.storage;

import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "worker")
@ConditionalOnProperty(prefix = "aiops.object-storage", name = "enabled", havingValue = "true")
public class MinioObjectStorageAdapter implements ObjectStoragePort {
  private static final long UNKNOWN_PART_SIZE = 10L * 1024L * 1024L;

  private final ObjectStorageProperties properties;
  private final MinioClient client;
  private final AtomicBoolean bucketReady = new AtomicBoolean();

  @Autowired
  public MinioObjectStorageAdapter(ObjectStorageProperties properties) {
    this(
        properties,
        MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .region(properties.region())
            .build());
  }

  MinioObjectStorageAdapter(ObjectStorageProperties properties, MinioClient client) {
    this.properties = properties;
    this.client = client;
  }

  @Override
  public StoredObject put(PutObjectCommand command, InputStream input) {
    if (command.sizeBytes() <= 0) {
      throw new IllegalArgumentException("object size must be positive");
    }
    return store(
        command.objectKey(),
        command.contentType(),
        input,
        command.sizeBytes(),
        command.sizeBytes());
  }

  @Override
  public StoredObject putUnknownLength(
      String objectKey, String contentType, InputStream input, long maxBytes) {
    if (maxBytes < 1) {
      throw new IllegalArgumentException("maxBytes must be positive");
    }
    return store(objectKey, contentType, input, -1, maxBytes);
  }

  @Override
  public InputStream get(String objectKey) {
    ObjectKeyPolicy.requireValid(objectKey);
    ensureBucket();
    try {
      return client.getObject(
          GetObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to read object", ex);
    }
  }

  @Override
  public InputStream get(String objectKey, long maxBytes) {
    if (maxBytes < 1) {
      throw new IllegalArgumentException("maxBytes must be positive");
    }
    return new CountingBoundedInputStream(get(objectKey), maxBytes);
  }

  @Override
  public StoredObject stat(String objectKey) {
    ObjectKeyPolicy.requireValid(objectKey);
    ensureBucket();
    try {
      var stat =
          client.statObject(
              StatObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
      return new StoredObject(objectKey, stat.size(), null);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to inspect object", ex);
    }
  }

  @Override
  public String presignedGet(String objectKey, Duration duration) {
    ObjectKeyPolicy.requireValid(objectKey);
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("download URL duration must be positive");
    }
    ensureBucket();
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(properties.bucket())
              .object(objectKey)
              .expiry(Math.toIntExact(duration.toSeconds()))
              .build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to create download URL", ex);
    }
  }

  @Override
  public void delete(String objectKey) {
    ObjectKeyPolicy.requireValid(objectKey);
    ensureBucket();
    try {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to delete object", ex);
    }
  }

  private StoredObject store(
      String objectKey, String contentType, InputStream source, long declaredSize, long maxBytes) {
    ObjectKeyPolicy.requireValid(objectKey);
    if (source == null) {
      throw new IllegalArgumentException("object input is required");
    }
    ensureBucket();
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      CountingBoundedInputStream bounded = new CountingBoundedInputStream(source, maxBytes);
      try (DigestInputStream input = new DigestInputStream(bounded, digest)) {
        PutObjectArgs.Builder builder =
            PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .contentType(normalizeContentType(contentType));
        builder.stream(input, declaredSize, declaredSize < 0 ? UNKNOWN_PART_SIZE : -1);
        client.putObject(builder.build());
      }
      if (declaredSize >= 0 && bounded.count() != declaredSize) {
        deleteQuietly(objectKey);
        throw new IllegalArgumentException("object size does not match declared size");
      }
      return new StoredObject(
          objectKey, bounded.count(), HexFormat.of().formatHex(digest.digest()));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      deleteQuietly(objectKey);
      throw new IllegalStateException("failed to store object", ex);
    }
  }

  private void ensureBucket() {
    if (bucketReady.get()) {
      return;
    }
    synchronized (bucketReady) {
      if (bucketReady.get()) {
        return;
      }
      try {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
          client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
        bucketReady.set(true);
      } catch (Exception ex) {
        throw new IllegalStateException("failed to initialize object storage bucket", ex);
      }
    }
  }

  private void deleteQuietly(String objectKey) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception ignored) {
      // Upload may have failed before the object was created.
    }
  }

  private static String normalizeContentType(String contentType) {
    return contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType.trim();
  }

  private static final class CountingBoundedInputStream extends FilterInputStream {
    private final long maxBytes;
    private long count;

    private CountingBoundedInputStream(InputStream delegate, long maxBytes) {
      super(delegate);
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value >= 0) {
        increment(1);
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int read = super.read(buffer, offset, length);
      if (read > 0) {
        increment(read);
      }
      return read;
    }

    private void increment(long value) {
      count += value;
      if (count > maxBytes) {
        throw new IllegalArgumentException("object exceeds maximum size of " + maxBytes + " bytes");
      }
    }

    private long count() {
      return count;
    }
  }
}
