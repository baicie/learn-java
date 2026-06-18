package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnsibleCommandPreviewBuilder {
  private final AnsibleJson json;

  public AnsibleCommandPreviewBuilder(ObjectMapper objectMapper) {
    this.json = new AnsibleJson(objectMapper);
  }

  public List<String> buildArgv(
      String binary,
      Path inventoryFile,
      Path playbookFile,
      AnsibleActionPayload payload,
      boolean checkMode) {
    List<String> argv = new ArrayList<>();
    argv.add(binary);

    if (checkMode) {
      argv.add("--check");
    }

    argv.add("-i");
    argv.add(inventoryFile.toString());
    argv.add(playbookFile.toString());

    if (payload.tags() != null && !payload.tags().isEmpty()) {
      argv.add("--tags");
      argv.add(String.join(",", payload.tags()));
    }

    if (payload.extraVars() != null && !payload.extraVars().isEmpty()) {
      argv.add("--extra-vars");
      argv.add(json.write(payload.extraVars()));
    }

    return argv;
  }

  public String toDisplayCommand(List<String> argv) {
    return argv.stream().map(this::quote).reduce((a, b) -> a + " " + b).orElse("");
  }

  private String quote(String arg) {
    if (arg == null || arg.isBlank()) {
      return "''";
    }

    if (arg.matches("[a-zA-Z0-9_./:=,@+-]+")) {
      return arg;
    }

    return "'" + arg.replace("'", "'\\''") + "'";
  }
}
