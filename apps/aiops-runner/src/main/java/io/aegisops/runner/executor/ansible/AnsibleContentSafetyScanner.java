package io.aegisops.runner.executor.ansible;

import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AnsibleContentSafetyScanner {
  private static final List<String> DENIED_PARTS =
      List.of(
          "ansible_password",
          "ansible_become_password",
          "ansible_ssh_private_key_file",
          "ansible_private_key_file",
          "vault_password_file",
          "private_key",
          "ssh_key",
          "password",
          "passwd",
          "token",
          "secret",
          "credential",
          "credentials",
          "vault");

  public void scanInventory(String content) {
    scan(
        "ANSIBLE_INVENTORY_SECRET_BLOCKED",
        "Ansible inventory contains credential-like content",
        content);
  }

  public void scanPlaybook(String content) {
    scan(
        "ANSIBLE_PLAYBOOK_SECRET_BLOCKED",
        "Ansible playbook contains credential-like content",
        content);
  }

  private void scan(String code, String message, String content) {
    if (content == null || content.isBlank()) {
      return;
    }

    String normalized = content.toLowerCase(Locale.ROOT).replace('-', '_');

    for (String part : DENIED_PARTS) {
      if (normalized.contains(part)) {
        throw new AppException(code, message + ": " + part);
      }
    }
  }
}
