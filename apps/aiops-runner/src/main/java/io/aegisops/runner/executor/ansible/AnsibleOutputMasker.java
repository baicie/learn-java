package io.aegisops.runner.executor.ansible;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AnsibleOutputMasker {
  private static final List<Pattern> PATTERNS =
      List.of(
          Pattern.compile("(?i)(password\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(passwd\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(token\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(secret\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(private_key\\s*[=:]\\s*)[^\\s,}]+"),
          Pattern.compile("(?i)(api_key\\s*[=:]\\s*)[^\\s,}]+"));

  public String mask(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String result = value;
    for (Pattern pattern : PATTERNS) {
      result = pattern.matcher(result).replaceAll("$1***");
    }
    return result;
  }
}
