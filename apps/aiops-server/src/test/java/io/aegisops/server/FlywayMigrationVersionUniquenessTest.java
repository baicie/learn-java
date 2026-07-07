package io.aegisops.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Flyway 迁移的硬性守门.
 *
 * <p>约束来自 SKILL.md §17 + AGENTS.md §3.9:
 *
 * <ul>
 *   <li>文件名必须严格匹配 {@code ^V\d{4}__init_[a-z_]+\.sql$}
 *   <li>版本号(前缀)必须唯一
 *   <li>版本号必须严格单调递增(跳号视为错误,防止 V0099 之后突然跳到 V0020)
 * </ul>
 *
 * <p>这条测试是 PR3 Flyway clean-slate 拆分的后续硬化,作为 V0006+ 增量迁移命名规范的 自动守门。任何新增 .sql 必须同时满足三项,否则构建失败。
 */
class FlywayMigrationVersionUniquenessTest {
  /** SKILL.md §17 命名规范: V + 4 位数字 + __init_ + 小写蛇形 + .sql. */
  private static final Pattern INIT_MIGRATION =
      Pattern.compile("^V(\\d{4})__init_([a-z][a-z0-9_]*)\\.sql$");

  /** 历史迁移白名单：在 V0008 引入 {@code init_} 命名规范之前存在的旧文件名，保留原始 文件名以避免已部署环境的 Flyway 校验风险。 */
  private static final Set<String> LEGACY_MIGRATION_WHITELIST =
      Set.of(
          "V0008__platform_navigation_workspace.sql",
          "V0009__alert_ingest_rules.sql",
          "V0010__evidence_collection_task.sql");

  /** 允许但已废弃的宽松正则,仅用于版本号唯一性兜底. */
  private static final Pattern ANY_VERSIONED_MIGRATION = Pattern.compile("^V([^_]+)__.+\\.sql$");

  @Test
  void allMigrationsFollowInitNamingConvention() throws Exception {
    List<String> names = migrationFileNames();
    List<String> offenders =
        names.stream()
            .filter(n -> !INIT_MIGRATION.matcher(n).matches())
            .filter(n -> !LEGACY_MIGRATION_WHITELIST.contains(n))
            .toList();

    assertEquals(
        List.of(),
        offenders,
        "Flyway migrations must match "
            + INIT_MIGRATION.pattern()
            + " (per SKILL.md §17) unless listed in LEGACY_MIGRATION_WHITELIST. Offenders: "
            + offenders);
  }

  @Test
  void migrationVersionsAreUnique() throws Exception {
    Map<String, Long> counts;
    try (var migrationFiles = migrationFiles()) {
      counts =
          migrationFiles
              .map(Path::getFileName)
              .map(Path::toString)
              .map(ANY_VERSIONED_MIGRATION::matcher)
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

  @Test
  void migrationVersionsAreStrictlyMonotonic() throws Exception {
    TreeMap<Integer, String> ordered = new TreeMap<>();
    Set<Integer> duplicates = new HashSet<>();

    for (String name : migrationFileNames()) {
      var matcher = ANY_VERSIONED_MIGRATION.matcher(name);
      if (!matcher.matches()) {
        continue;
      }
      int version = Integer.parseInt(matcher.group(1));
      if (ordered.containsKey(version)) {
        duplicates.add(version);
      } else {
        ordered.put(version, name);
      }
    }

    assertTrue(
        duplicates.isEmpty(),
        "Flyway migration versions must be unique. Duplicates: " + duplicates);

    int previous = -1;
    for (int version : ordered.keySet()) {
      assertTrue(
          version > previous,
          "Flyway migration versions must be strictly increasing. "
              + "Found "
              + ordered.get(version)
              + " (version "
              + version
              + ") after version "
              + previous
              + ". Gaps are allowed; regressions are not.");
      previous = version;
    }
  }

  private List<String> migrationFileNames() throws java.io.IOException, URISyntaxException {
    var resource = getClass().getClassLoader().getResource("db/migration");
    if (resource == null) {
      throw new IllegalStateException("db/migration resource directory is missing");
    }
    try (var stream = Files.list(Path.of(resource.toURI()))) {
      return stream.map(Path::getFileName).map(Path::toString).sorted().toList();
    }
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
