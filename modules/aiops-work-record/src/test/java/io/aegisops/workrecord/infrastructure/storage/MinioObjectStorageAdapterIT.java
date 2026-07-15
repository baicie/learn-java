package io.aegisops.workrecord.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.workrecord.application.port.ObjectStoragePort.PutObjectCommand;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class MinioObjectStorageAdapterIT {
  private static final String ACCESS_KEY = "phase20-access";
  private static final String SECRET_KEY = "phase20-secret-key";

  @Container
  static final GenericContainer<?> MINIO =
      new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
          .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
          .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
          .withCommand("server", "/data")
          .withExposedPorts(9000)
          .waitingFor(Wait.forListeningPort());

  @Test
  void storesReadsSignsAndDeletesObjectsAgainstRealMinio() throws Exception {
    MinioObjectStorageAdapter adapter = adapter();
    byte[] content = "phase-20-object".getBytes(StandardCharsets.UTF_8);

    var stored =
        adapter.put(
            new PutObjectCommand("tenant-1/async/job-1/result.txt", "text/plain", content.length),
            new ByteArrayInputStream(content));

    assertThat(stored.sizeBytes()).isEqualTo(content.length);
    assertThat(stored.sha256()).hasSize(64);
    try (var input = adapter.get(stored.objectKey())) {
      assertThat(input.readAllBytes()).isEqualTo(content);
    }
    assertThat(adapter.presignedGet(stored.objectKey(), Duration.ofMinutes(5)))
        .contains("tenant-1/async/job-1/result.txt")
        .contains("X-Amz-Signature");

    adapter.delete(stored.objectKey());
    assertThatThrownBy(
            () -> {
              try (var input = adapter.get(stored.objectKey())) {
                input.read();
              }
            })
        .isInstanceOf(Exception.class);
  }

  @Test
  void unknownLengthUploadStopsAtConfiguredLimit() {
    byte[] content = "too-large".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () ->
                adapter()
                    .putUnknownLength(
                        "tenant-1/async/job-2/source.bin",
                        "application/octet-stream",
                        new ByteArrayInputStream(content),
                        content.length - 1L))
        .isInstanceOf(RuntimeException.class);
  }

  private static MinioObjectStorageAdapter adapter() {
    return new MinioObjectStorageAdapter(
        new ObjectStorageProperties(
            "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000),
            ACCESS_KEY,
            SECRET_KEY,
            "phase20-test",
            1024,
            300,
            null));
  }
}
