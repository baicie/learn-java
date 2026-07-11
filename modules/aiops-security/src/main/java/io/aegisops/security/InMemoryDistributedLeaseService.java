package io.aegisops.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内存版分布式租约实现，仅用于单实例部署。
 *
 * <p>使用 {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} 实现
 * 获取与释放的原子性，避免并发场景下覆盖未过期租约。
 *
 * <p>租约条目会带 {@code expiresAtMillis}，获取时若已过期则视为无主并允许重新抢占，
 * 从而保证 TTL 契约在持有者未调用 {@link DistributedLease#close()}（死锁、异常中断等）
 * 时仍能在到期后释放。
 */
public class InMemoryDistributedLeaseService implements DistributedLeaseService {

  private final ConcurrentMap<String, LeaseEntry> leases = new ConcurrentHashMap<>();

  private final Clock clock;

  public InMemoryDistributedLeaseService() {
    this(Clock.systemUTC());
  }

  InMemoryDistributedLeaseService(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Optional<DistributedLease> tryAcquire(String key, Duration ttl) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("lease key is required");
    }

    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("lease ttl must be positive");
    }

    long now = clock.millis();
    String token = UUID.randomUUID().toString();

    LeaseEntry selected =
        leases.compute(
            key,
            (ignored, current) -> {
              if (current == null || current.expiresAtMillis <= now) {
                return new LeaseEntry(token, now + ttl.toMillis());
              }
              return current;
            });

    if (!token.equals(selected.token)) {
      return Optional.empty();
    }

    return Optional.of(new InMemoryLease(key, token));
  }

  private final class InMemoryLease implements DistributedLease {

    private final String key;
    private final String token;
    private final AtomicBoolean closed = new AtomicBoolean();

    private InMemoryLease(String key, String token) {
      this.key = key;
      this.token = token;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }

      leases.computeIfPresent(
          key,
          (ignored, current) -> token.equals(current.token) ? null : current);
    }
  }

  private static final class LeaseEntry {
    private final String token;
    private final long expiresAtMillis;

    private LeaseEntry(String token, long expiresAtMillis) {
      this.token = token;
      this.expiresAtMillis = expiresAtMillis;
    }
  }
}
