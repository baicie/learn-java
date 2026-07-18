package io.aegisops.platform.iam.service;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.domain.PermissionRisk;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.error.IamErrorCode;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Validates and normalizes the permission set requested by an operator against the immutable {@code
 * iam.permission_definition} directory.
 *
 * <p>Two responsibilities:
 *
 * <ol>
 *   <li>Reject any permission code that is not present in the directory (security: prevents ad-hoc
 *       privilege grant from being persisted).
 *   <li>Re-resolve <em>dependencies</em> implicitly — if the operator asks for a permission that
 *       requires reading users first, "platform:user:read" is auto-included.
 * </ol>
 */
@Component
public class PermissionNormalizer {

  private final PermissionDefinitionRepository repository;

  public PermissionNormalizer(PermissionDefinitionRepository repository) {
    this.repository = repository;
  }

  public NormalizedPermissionSet normalize(Set<String> requested) {
    Set<String> safeInput = requested == null ? Set.of() : sanitize(requested);
    if (safeInput.isEmpty()) {
      return new NormalizedPermissionSet(Set.of(), Set.of(), Set.of());
    }
    Set<String> requestedCodes = new LinkedHashSet<>(safeInput);
    Map<String, PermissionDefinition> directory =
        repository.listAll().stream()
            .collect(Collectors.toMap(PermissionDefinition::code, Function.identity()));
    Set<PermissionDefinition> definitions = resolveDefinitions(requestedCodes, directory);

    Set<String> foundCodes = new HashSet<>();
    for (PermissionDefinition def : definitions) {
      foundCodes.add(def.code());
    }
    Set<String> missing = new LinkedHashSet<>(requestedCodes);
    missing.removeAll(foundCodes);
    if (!missing.isEmpty()) {
      throw new IamDomainException(
          IamErrorCode.PERMISSION_NOT_FOUND,
          "permission directory missing one or more entries: " + missing);
    }

    Set<String> finalSet = new HashSet<>();
    for (PermissionDefinition def : definitions) {
      if (!def.enabled()) {
        continue;
      }
      finalSet.add(def.code());
      finalSet.addAll(def.dependencies());
    }

    Set<String> criticalCodes = new LinkedHashSet<>();
    for (PermissionDefinition def : definitions) {
      if (def.enabled() && def.risk() == PermissionRisk.CRITICAL) {
        criticalCodes.add(def.code());
      }
    }

    return new NormalizedPermissionSet(finalSet, requestedCodes, Set.copyOf(criticalCodes));
  }

  private Set<PermissionDefinition> resolveDefinitions(
      Set<String> requested, Map<String, PermissionDefinition> directory) {
    Set<PermissionDefinition> resolved = new LinkedHashSet<>();
    Set<String> visited = new HashSet<>();
    var pending = new ArrayDeque<>(requested);
    while (!pending.isEmpty()) {
      String code = pending.removeFirst();
      if (!visited.add(code)) {
        continue;
      }
      PermissionDefinition definition = directory.get(code);
      if (definition == null) {
        throw new IamDomainException(
            requested.contains(code)
                ? IamErrorCode.PERMISSION_NOT_FOUND
                : IamErrorCode.PERMISSION_DIRECTORY_INCOMPLETE,
            "permission directory missing one or more entries: [" + code + "]");
      }
      if (!definition.enabled()) {
        throw new IamDomainException(
            IamErrorCode.PERMISSION_DIRECTORY_INCOMPLETE,
            "permission directory contains disabled dependency: [" + code + "]");
      }
      resolved.add(definition);
      pending.addAll(definition.dependencies());
    }
    return resolved;
  }

  private Set<String> sanitize(Set<String> raw) {
    if (raw == null) {
      return Set.of();
    }
    Set<String> clean = new LinkedHashSet<>();
    for (String value : raw) {
      if (value != null && !value.isBlank()) {
        clean.add(value.trim());
      }
    }
    return clean;
  }

  /** Output envelope returned by {@link #normalize(Set)}. */
  public record NormalizedPermissionSet(
      Set<String> permissions, Set<String> requested, Set<String> criticalCodes) {}
}
