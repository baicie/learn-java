package io.aegisops.security;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryDistributedLeaseService implements DistributedLeaseService {

  private final ConcurrentMap<String, String> leases = new ConcurrentHashMap<>();

  @Override
  public Optional<DistributedLease> tryAcquire(String key, Duration ttl) {
    String token = UUID.randomUUID().toString();
    String previous = leases.putIfAbsent(key, token);
    if (previous != null) {
      return Optional.empty();
    }

    return Optional.of(new InMemoryLease(key, token));
  }

  private final class InMemoryLease implements DistributedLease {

    private final String key;
    private final String token;
    private boolean closed;

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
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
      }
      leases.remove(key, token);
    }
  }
}