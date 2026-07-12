package io.aegisops.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.platform.iam.domain.PlatformUserQuery;
import io.aegisops.platform.iam.domain.PlatformUserStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformUserQueryTest {

  @Test
  void should_clamp_page_and_pagesize() {
    PlatformUserQuery query =
        new PlatformUserQuery("alice", PlatformUserStatus.ACTIVE, Set.of("admin"), -1, 0).normalized();

    assertThat(query.page()).isEqualTo(1);
    assertThat(query.pageSize()).isEqualTo(20);
    assertThat(query.status()).isEqualTo(PlatformUserStatus.ACTIVE);
  }

  @Test
  void should_trim_keyword() {
    PlatformUserQuery query =
        new PlatformUserQuery("   bob   ", null, Set.of(), 1, 20).normalized();
    assertThat(query.keyword()).isEqualTo("bob");
  }

  @Test
  void should_cap_huge_pagesize() {
    PlatformUserQuery query =
        new PlatformUserQuery(null, null, null, 1, 9999).normalized();
    assertThat(query.pageSize()).isEqualTo(200);
  }

  @Test
  void should_default_empty_role_set_for_null() {
    PlatformUserQuery query =
        new PlatformUserQuery(null, null, null, 1, 20).normalized();
    assertThat(query.roleCodes()).isEmpty();
  }

  @Test
  void should_reject_null_status_input_via_normalized() {
    PlatformUserQuery query =
        new PlatformUserQuery(null, null, Set.of("a"), 1, 20).normalized();
    assertThat(query.status()).isNull();
    // Sanity check on still-string-rejection path: throwaway exception path
    assertThatThrownBy(() -> PlatformUserStatus.from("made-up"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}