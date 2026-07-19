package io.aegisops.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class WorkerRestClientRuntimeContractIT {

  @Test
  void executableJarContainsRestClientAutoConfiguration() throws IOException {
    Path executableJar;
    try (var artifacts = Files.list(Path.of("target"))) {
      executableJar =
          artifacts
              .filter(path -> path.getFileName().toString().matches("aiops-worker-.*\\.jar"))
              .findFirst()
              .orElseThrow();
    }

    try (ZipFile jar = new ZipFile(executableJar.toFile())) {
      assertThat(jar.stream().map(entry -> entry.getName()))
          .anyMatch(name -> name.startsWith("BOOT-INF/lib/spring-boot-restclient-"));
    }
  }
}
