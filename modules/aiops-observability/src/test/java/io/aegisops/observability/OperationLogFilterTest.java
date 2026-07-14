package io.aegisops.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.common.security.AuthenticatedActor;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class OperationLogFilterTest {

  @Test
  void shouldReadActorIdFromCommonContract() {
    AuthenticationFixture actor = new AuthenticationFixture("user-1", "tenant-1");
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(actor, null);

    assertThat(OperationLogFilter.actorId(authentication)).isEqualTo("user-1");
  }

  @Test
  void shouldIgnoreUnknownPrincipalTypes() {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("anonymous", null);

    assertThat(OperationLogFilter.actorId(authentication)).isNull();
    assertThat(OperationLogFilter.actorId(null)).isNull();
  }

  private record AuthenticationFixture(String id, String tenantId) implements AuthenticatedActor {}
}
