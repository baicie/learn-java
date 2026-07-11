package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryDistributedLeaseServiceTest {

  @Test
  void expiredLeaseMustBeReacquirable() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
    InMemoryDistributedLeaseService service = new InMemoryDistributedLeaseService(clock);

    Optional<DistributedLease> first =
        service.tryAcquire("export:user-1", Duration.ofSeconds(30));
    assertThat(first).isPresent();

    Optional<DistributedLease> concurrent =
        service.tryAcquire("export:user-1", Duration.ofSeconds(30));
    assertThat(concurrent).isEmpty();

    first.get().close();

    Optional<DistributedLease> reacquired =
        service.tryAcquire("export:user-1", Duration.ofSeconds(30));
    assertThat(reacquired).isPresent();
  }

  @Test
  void leaseMustBeReleasedEvenWithoutClose() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
    InMemoryDistributedLeaseService service = new InMemoryDistributedLeaseService(clock);

    assertThat(service.tryAcquire("export:user-1", Duration.ofSeconds(30))).isPresent();
    // 持有者忘记 close，模拟死锁 / 异常中断。

    clock.advance(Duration.ofSeconds(31));

    assertThat(service.tryAcquire("export:user-1", Duration.ofSeconds(30))).isPresent();
  }

  @Test
  void ttlMustBePositive() {
    InMemoryDistributedLeaseService service = new InMemoryDistributedLeaseService();

    assertThatThrownBy(() -> service.tryAcquire("k", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ttl");
  }

  @Test
  void keyMustNotBeBlank() {
    InMemoryDistributedLeaseService service = new InMemoryDistributedLeaseService();

    assertThatThrownBy(() -> service.tryAcquire("", Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
  }

  @Test
  void closeMustBeIdempotent() {
    MutableClock clock = new MutableClock(Instant.parse("2026-07-11T00:00:00Z"));
    InMemoryDistributedLeaseService service = new InMemoryDistributedLeaseService(clock);

    DistributedLease lease =
        service.tryAcquire("export:user-1", Duration.ofSeconds(30)).orElseThrow();
    lease.close();
    lease.close();
    // 第二次 close 不应抛错，且不应误删其他人的租约。
    assertThat(service.tryAcquire("export:user-1", Duration.ofSeconds(30))).isPresent();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    void advance(Duration delta) {
      this.now = this.now.plus(delta);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public long millis() {
      return now.toEpochMilli();
    }
  }
}
