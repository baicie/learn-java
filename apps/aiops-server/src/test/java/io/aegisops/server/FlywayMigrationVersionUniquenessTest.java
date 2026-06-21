package io.aegisops.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionUniquenessTest {
  private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.+\\.sql$");

  @Test
  void migrationVersionsAreUnique() throws Exception {
    Map<String, Long> counts;
    try (var migrationFiles = migrationFiles()) {
      counts =
          migrationFiles
              .map(Path::getFileName)
              .map(Path::toString)
              .map(VERSIONED_MIGRATION::matcher)
              .filter(java.util.regex.Matcher::matches)
              .map(matcher -> matcher.group(1))
              .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    Map<String, Long> duplicates =
        counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertEquals(Map.of(), duplicates, "Flyway migration versions must be unique");
  }

  private java.util.stream.Stream<Path> migrationFiles()
      throws java.io.IOException, URISyntaxException {
    var resource = getClass().getClassLoader().getResource("db/migration");
    if (resource == null) {
      throw new IllegalStateException("db/migration resource directory is missing");
    }
    return Files.list(Path.of(resource.toURI()));
  }
}
