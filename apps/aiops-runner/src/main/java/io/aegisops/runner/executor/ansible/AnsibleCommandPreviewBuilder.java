package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnsibleCommandPreviewBuilder {
  private final AnsibleJson json;

  public AnsibleCommandPreviewBuilder(ObjectMapper objectMapper) {
    this.json = new AnsibleJson(objectMapper);
  }

  public String build(
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsibleActionPayload payload,
      boolean checkMode) {
    List<String> parts = new ArrayList<>();
    parts.add("ansible-playbook");

    if (checkMode) {
      parts.add("--check");
    }

    parts.add("-i");
    parts.add(displayInventory(inventory));
    parts.add(displayPlaybook(playbook));

    if (payload.tags() != null && !payload.tags().isEmpty()) {
      parts.add("--tags");
      parts.add(String.join(",", payload.tags()));
    }

    if (payload.extraVars() != null && !payload.extraVars().isEmpty()) {
      parts.add("--extra-vars");
      parts.add("'" + json.write(payload.extraVars()).replace("'", "'\\''") + "'");
    }

    return String.join(" ", parts);
  }

  private String displayInventory(AnsibleInventoryRecord inventory) {
    if ("file_ref".equals(inventory.inventoryType())) {
      return inventory.fileRef();
    }
    return "<inline-inventory:" + inventory.name() + ">";
  }

  private String displayPlaybook(AnsiblePlaybookRecord playbook) {
    if (playbook.playbookRef() != null && !playbook.playbookRef().isBlank()) {
      return playbook.playbookRef();
    }
    return "<inline-playbook:" + playbook.name() + ">";
  }
}
