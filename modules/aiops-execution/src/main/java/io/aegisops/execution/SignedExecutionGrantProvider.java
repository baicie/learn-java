package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.Ed25519KeyLoader;
import io.aegisops.common.security.ExecutionGrantClaims;
import io.aegisops.common.security.ExecutionGrantCodec;
import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "aiops.runtime", name = "app", havingValue = "app")
@EnableConfigurationProperties(ExecutionGrantProperties.class)
public class SignedExecutionGrantProvider implements ExecutionGrantProvider {
  private final ExecutionGrantProperties properties;
  private final ExecutionGrantCodec codec;
  private final ExecutionSnapshotHasher snapshotHasher;
  private final Clock clock;
  private volatile PrivateKey privateKey;

  @Autowired
  public SignedExecutionGrantProvider(
      ExecutionGrantProperties properties, ObjectMapper objectMapper) {
    this(
        properties,
        new ExecutionGrantCodec(objectMapper, Clock.systemUTC()),
        new ExecutionSnapshotHasher(objectMapper),
        Clock.systemUTC(),
        null);
  }

  SignedExecutionGrantProvider(
      ExecutionGrantProperties properties,
      ExecutionGrantCodec codec,
      ExecutionSnapshotHasher snapshotHasher,
      Clock clock,
      PrivateKey privateKey) {
    properties.validateMetadata();
    this.properties = properties;
    this.codec = codec;
    this.snapshotHasher = snapshotHasher;
    this.clock = clock;
    this.privateKey = privateKey;
  }

  @Override
  public IssuedExecutionGrant issue(
      ExecutionRunCreateCommand run, List<ExecutionStepCreateCommand> executionSteps) {
    String snapshotSha256 = snapshotHasher.sha256(run, executionSteps);
    Instant issuedAt = Instant.ofEpochSecond(clock.instant().getEpochSecond());
    Instant expiresAt =
        issuedAt.plusSeconds(Math.addExact(run.timeoutSeconds(), properties.getQueueWaitSeconds()));
    ExecutionGrantClaims claims =
        new ExecutionGrantClaims(
            properties.getIssuer(),
            "execution:" + run.id(),
            Set.of(properties.getAudience()),
            List.of(properties.getScope()),
            run.tenantId(),
            run.incidentId(),
            run.id(),
            run.planId(),
            run.mode(),
            run.executionKind(),
            run.rollbackPlanId(),
            run.rollbackOfExecutionId(),
            snapshotSha256,
            run.timeoutSeconds(),
            issuedAt,
            expiresAt,
            UUID.randomUUID().toString());

    return new IssuedExecutionGrant(
        codec.issue(signingKey(), properties.getKeyId(), claims),
        snapshotSha256,
        OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
  }

  private PrivateKey signingKey() {
    PrivateKey loaded = privateKey;
    if (loaded != null) {
      return loaded;
    }
    synchronized (this) {
      if (privateKey == null) {
        properties.validateSigningKey();
        try {
          privateKey =
              Ed25519KeyLoader.loadPrivateKey(
                  Files.readString(Path.of(properties.getPrivateKeyFile())));
        } catch (RuntimeException exception) {
          throw exception;
        } catch (Exception exception) {
          throw new IllegalStateException("Unable to read execution grant private key", exception);
        }
      }
      return privateKey;
    }
  }
}
