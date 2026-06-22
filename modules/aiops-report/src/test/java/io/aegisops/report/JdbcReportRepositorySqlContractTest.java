package io.aegisops.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcReportRepositorySqlContractTest {
  @Test
  void incidentQueryShouldUseConfidenceColumnAsRcaConfidenceAlias() throws IOException {
    List<Path> matches =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith("JdbcReportRepository.java"))
            .toList();

    assertThat(matches).isNotEmpty();
    String source = Files.readString(matches.get(0));
    assertThat(source).contains("confidence as rca_confidence");
    assertThat(source).doesNotContain("suspected_root_cause, rca_confidence");
  }
}
