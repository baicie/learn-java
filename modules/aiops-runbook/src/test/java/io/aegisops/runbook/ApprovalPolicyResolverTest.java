package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalPolicyResolverTest {
  @Test
  void tenantPolicyOverridesGlobalPolicy() {
    FakePolicyRepository repository = new FakePolicyRepository();
    repository.tenantPolicy =
        new ApprovalPolicyRecord(
            "tenant_high",
            "tenant_1",
            "high",
            2,
            true,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());
    repository.globalPolicy =
        new ApprovalPolicyRecord(
            "global_high", null, "high", 1, true, true, OffsetDateTime.now(), OffsetDateTime.now());

    ApprovalPolicyResolver resolver = new ApprovalPolicyResolver(repository);

    ApprovalPolicyRecord result = resolver.resolve("tenant_1", "high");

    assertEquals("tenant_high", result.id());
    assertEquals(2, result.requiredApprovals());
  }

  @Test
  void fallbackCriticalRequiresTwoApprovals() {
    ApprovalPolicyResolver resolver = new ApprovalPolicyResolver(new FakePolicyRepository());

    ApprovalPolicyRecord result = resolver.resolve("tenant_1", "critical");

    assertEquals("critical", result.riskLevel());
    assertEquals(2, result.requiredApprovals());
    assertTrue(result.requireComment());
  }

  private static class FakePolicyRepository extends FakeRunbookRepositoryBase {
    ApprovalPolicyRecord tenantPolicy;
    ApprovalPolicyRecord globalPolicy;

    @Override
    public Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel) {
      return Optional.ofNullable(tenantPolicy);
    }

    @Override
    public Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel) {
      return Optional.ofNullable(globalPolicy);
    }
  }
}
