package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordUserLookupService {
  private static final int MAX_IDS = 100;
  private final WorkRecordUserPort users;

  public WorkRecordUserLookupService(WorkRecordUserPort users) {
    this.users = users;
  }

  public Map<String, String> displayNames(String tenantId, Collection<String> userIds) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    if (userIds != null) {
      userIds.stream()
          .filter(value -> value != null && !value.isBlank())
          .map(String::trim)
          .forEach(normalized::add);
    }
    if (normalized.size() > MAX_IDS) {
      throw new IllegalArgumentException("at most 100 user ids are allowed");
    }
    return normalized.isEmpty() ? Map.of() : users.displayNames(tenantId, normalized);
  }
}
