package io.aegisops.runner.executor.ansible;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AnsibleOutputMasker {
  private static final List<PatternReplacement> PATTERNS =
      List.of(
          new PatternReplacement(
              Pattern.compile(
                  "(?i)([\"']?(?:password|passwd|pwd|token|secret|private[_-]?key|api[_-]?key|credential|credentials|vault)[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s,}]+)([\"']?)"),
              "$1***$3"),
          new PatternReplacement(
              Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/=-]+", Pattern.CASE_INSENSITIVE),
              "$1***"),
          new PatternReplacement(
              Pattern.compile("(?i)(basic\\s+)[a-z0-9._~+/=-]+", Pattern.CASE_INSENSITIVE),
              "$1***"));

  public String mask(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String result = value;
    for (PatternReplacement pr : PATTERNS) {
      result = pr.pattern.matcher(result).replaceAll(pr.replacement);
    }
    return result;
  }

  private record PatternReplacement(Pattern pattern, String replacement) {}
}
