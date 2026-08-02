package io.aegisops.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Architecture guard for PR2.
 *
 * <p>Every AegisOps backend app must declare {@code aiops.runtime.phase} in its {@code
 * application.yml}. This test walks the reactor working tree, parses each app's {@code
 * application.yml}, and asserts:
 *
 * <ul>
 *   <li>{@code aiops.runtime.phase} is present and non-blank
 *   <li>its value is one of the seven {@link RuntimePhase} wire labels ({@code phase0} .. {@code
 *       phase6})
 * </ul>
 *
 * <p>Without this guard a developer can accidentally delete the phase key (silently downgrading the
 * runtime to {@code PHASE_0}) without breaking any unit test, which is the exact failure mode PR2
 * was created to prevent.
 */
class ApplicationYmlRuntimePhaseTest {

  private static final Set<String> APPS =
      Set.of(
          "apps/aiops-server/src/main/resources/application.yml",
          "apps/aiops-runner/src/main/resources/application.yml");

  @Test
  @DisplayName("every backend app application.yml declares a valid aiops.runtime.phase")
  void everyAppDeclaresValidRuntimePhase() throws IOException {
    Path workingTree = reactorWorkingTree();
    Set<String> missing = new LinkedHashSet<>();
    Set<String> invalid = new LinkedHashSet<>();

    for (String relativePath : APPS) {
      Path ymlPath = workingTree.resolve(relativePath);
      if (!Files.exists(ymlPath)) {
        missing.add(relativePath);
        continue;
      }

      Map<String, Object> root = parseYaml(ymlPath);
      Object phaseValue = navigate(root, "aiops", "runtime", "phase");
      if (phaseValue == null || phaseValue.toString().isBlank()) {
        invalid.add(relativePath + " -> aiops.runtime.phase is missing");
        continue;
      }
      String rawValue = phaseValue.toString();
      String effectiveWireLabel = resolvePlaceholder(rawValue);
      try {
        RuntimePhase phase = RuntimePhase.fromPropertyName(effectiveWireLabel);
        // Round-trip is part of the contract: phase5 from yaml must produce phase5 on the wire.
        assertThat(phase.propertyName())
            .as("wire-label round-trip for " + relativePath)
            .isEqualTo(effectiveWireLabel);
      } catch (IllegalArgumentException e) {
        invalid.add(
            relativePath
                + " -> aiops.runtime.phase="
                + rawValue
                + " (resolved to '"
                + effectiveWireLabel
                + "') is not a valid RuntimePhase wire label");
      }
    }

    assertThat(missing).as("missing application.yml files").isEmpty();
    assertThat(invalid).as("invalid aiops.runtime.phase declarations").isEmpty();
  }

  /**
   * Resolve {@code ${ENV_VAR:default}} placeholders to their default value. Returns the input
   * unchanged when no placeholder syntax is detected. Does not consult the OS environment — we only
   * validate that the yml declares a valid fallback.
   */
  static String resolvePlaceholder(String rawValue) {
    if (rawValue == null) {
      return null;
    }
    String trimmed = rawValue.trim();
    if (!trimmed.startsWith("${") || !trimmed.endsWith("}")) {
      return trimmed;
    }
    String inner = trimmed.substring(2, trimmed.length() - 1);
    int colon = inner.indexOf(':');
    if (colon < 0) {
      return inner;
    }
    return inner.substring(colon + 1);
  }

  private static Object navigate(Map<String, Object> root, String... keys) {
    Object cursor = root;
    for (String key : keys) {
      if (!(cursor instanceof Map<?, ?> map)) {
        return null;
      }
      cursor = map.get(key);
    }
    return cursor;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseYaml(Path ymlPath) throws IOException {
    try (var reader = Files.newBufferedReader(ymlPath)) {
      Object loaded = new Yaml().load(reader);
      if (loaded == null) {
        return Map.of();
      }
      if (!(loaded instanceof Map<?, ?>)) {
        throw new IOException(
            "Expected top-level YAML map in " + ymlPath + " but got " + loaded.getClass());
      }
      return (Map<String, Object>) loaded;
    }
  }

  /**
   * Find the reactor working tree root by walking upward from the test classpath until we find a
   * directory that contains an {@code apps/} child.
   */
  private static Path reactorWorkingTree() {
    Path cwd = Path.of("").toAbsolutePath();
    Path cursor = cwd;
    for (int i = 0; i < 8 && cursor != null; i++) {
      if (Files.isDirectory(cursor.resolve("apps"))) {
        return cursor;
      }
      cursor = cursor.getParent();
    }
    throw new IllegalStateException(
        "Could not locate reactor working tree from "
            + cwd
            + " (no 'apps/' directory within 8 levels)");
  }
}
