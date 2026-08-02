package io.aegisops.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.Ed25519KeyLoader;
import io.aegisops.common.security.ExecutionGrantClaims;
import io.aegisops.common.security.ExecutionGrantCodec;
import io.aegisops.common.security.InvalidExecutionGrantException;
import io.aegisops.execution.ExecutionSnapshotHasher;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RunnerExecutionGrantVerifier implements ExecutionGrantValidator {
  private final RunnerExecutionGrantProperties properties;
  private final ExecutionGrantCodec codec;
  private final ExecutionSnapshotHasher snapshotHasher;
  private final ObjectMapper objectMapper;
  private volatile Map<String, PublicKey> verificationKeys;

  @Autowired
  public RunnerExecutionGrantVerifier(
      RunnerExecutionGrantProperties properties, ObjectMapper objectMapper) {
    this(
        properties,
        new ExecutionGrantCodec(objectMapper, Clock.systemUTC()),
        new ExecutionSnapshotHasher(objectMapper),
        objectMapper,
        null);
  }

  RunnerExecutionGrantVerifier(
      RunnerExecutionGrantProperties properties,
      ExecutionGrantCodec codec,
      ExecutionSnapshotHasher snapshotHasher,
      Map<String, PublicKey> verificationKeys) {
    this(properties, codec, snapshotHasher, new ObjectMapper(), verificationKeys);
  }

  RunnerExecutionGrantVerifier(
      RunnerExecutionGrantProperties properties,
      ExecutionGrantCodec codec,
      ExecutionSnapshotHasher snapshotHasher,
      ObjectMapper objectMapper,
      Map<String, PublicKey> verificationKeys) {
    properties.validateMetadata();
    this.properties = properties;
    this.codec = codec;
    this.snapshotHasher = snapshotHasher;
    this.objectMapper = objectMapper;
    this.verificationKeys = verificationKeys == null ? null : Map.copyOf(verificationKeys);
  }

  @Override
  public void validate(ExecutionRunRecord run, List<ExecutionStepRecord> executionSteps) {
    try {
      requirePersistedGrant(run);
      requireStepBindings(run, executionSteps);
      requireLiveApprovalSnapshot(run);

      ExecutionGrantClaims claims =
          codec.verify(keys(), run.executionGrant(), properties.getAudience());
      requireEquals(properties.getIssuer(), claims.issuer());
      requireEquals(Set.of(properties.getAudience()), claims.audiences());
      requireScope(claims);
      requireEquals("execution:" + run.id(), claims.subject());
      requireEquals(run.tenantId(), claims.tenantId());
      requireEquals(run.incidentId(), claims.incidentId());
      requireEquals(run.id(), claims.executionId());
      requireEquals(run.planId(), claims.planId());
      requireEquals(run.mode(), claims.mode());
      requireEquals(run.executionKind(), claims.executionKind());
      requireEquals(run.rollbackPlanId(), claims.rollbackPlanId());
      requireEquals(run.rollbackOfExecutionId(), claims.rollbackOfExecutionId());
      if (claims.maxDurationSeconds() != run.timeoutSeconds()) {
        reject();
      }
      if (run.startedAt() == null
          || run.startedAt()
              .toInstant()
              .plusSeconds(run.timeoutSeconds())
              .isAfter(claims.expiresAt())) {
        reject();
      }
      if (run.executionGrantExpiresAt().toInstant().getEpochSecond()
          != claims.expiresAt().getEpochSecond()) {
        reject();
      }

      String actualSnapshot = snapshotHasher.sha256(run, executionSteps);
      requireEquals(run.executionSnapshotSha256(), claims.snapshotSha256());
      requireEquals(run.executionSnapshotSha256(), actualSnapshot);
    } catch (InvalidExecutionGrantException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidExecutionGrantException("Execution grant validation failed", exception);
    }
  }

  private void requirePersistedGrant(ExecutionRunRecord run) {
    if (run == null
        || isBlank(run.executionGrant())
        || isBlank(run.executionSnapshotSha256())
        || !run.executionSnapshotSha256().matches("[0-9a-f]{64}")
        || run.executionGrantExpiresAt() == null) {
      reject();
    }
  }

  private void requireStepBindings(
      ExecutionRunRecord run, List<ExecutionStepRecord> executionSteps) {
    if (executionSteps == null
        || executionSteps.stream()
            .anyMatch(
                step ->
                    !Objects.equals(run.tenantId(), step.tenantId())
                        || !Objects.equals(run.id(), step.executionId()))) {
      reject();
    }
  }

  private void requireLiveApprovalSnapshot(ExecutionRunRecord run) {
    if (!"live".equals(run.mode())) {
      return;
    }
    if (isBlank(run.approvalId()) || isBlank(run.approvalSnapshotJson())) {
      reject();
    }
    try {
      JsonNode snapshot = objectMapper.readTree(run.approvalSnapshotJson());
      if (snapshot == null
          || !snapshot.isObject()
          || !run.approvalId().equals(snapshot.path("approvalId").asText())
          || !snapshot.path("planId").asText().equals(run.planId())
          || !"approved".equalsIgnoreCase(snapshot.path("status").asText())) {
        reject();
      }
      int requiredApprovals = snapshot.path("requiredApprovals").asInt(-1);
      int approvedCount = snapshot.path("approvedCount").asInt(-1);
      if (requiredApprovals < 1 || approvedCount < requiredApprovals) {
        reject();
      }
    } catch (InvalidExecutionGrantException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException("Execution grant validation failed", exception);
    }
  }

  private void requireScope(ExecutionGrantClaims claims) {
    if (!List.of(properties.getScope()).equals(claims.scopes())) {
      reject();
    }
  }

  private Map<String, PublicKey> keys() {
    Map<String, PublicKey> loaded = verificationKeys;
    if (loaded != null) {
      return loaded;
    }
    synchronized (this) {
      if (verificationKeys == null) {
        verificationKeys = loadKeys();
      }
      return verificationKeys;
    }
  }

  private Map<String, PublicKey> loadKeys() {
    properties.validateKeyFiles();
    try {
      Map<String, PublicKey> keys = new LinkedHashMap<>();
      keys.put(
          properties.getCurrentKeyId(),
          Ed25519KeyLoader.loadPublicKey(
              Files.readString(Path.of(properties.getCurrentPublicKeyFile()))));
      if (!isBlank(properties.getPreviousKeyId())) {
        keys.put(
            properties.getPreviousKeyId(),
            Ed25519KeyLoader.loadPublicKey(
                Files.readString(Path.of(properties.getPreviousPublicKeyFile()))));
      }
      return Map.copyOf(keys);
    } catch (RuntimeException exception) {
      throw new InvalidExecutionGrantException(
          "Execution grant verification keys are invalid", exception);
    } catch (Exception exception) {
      throw new InvalidExecutionGrantException(
          "Unable to read execution grant verification keys", exception);
    }
  }

  private static void requireEquals(Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      reject();
    }
  }

  private static void reject() {
    throw new InvalidExecutionGrantException("Execution grant validation failed");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
