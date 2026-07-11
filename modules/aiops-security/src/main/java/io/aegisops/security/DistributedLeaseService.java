package io.aegisops.security;

import java.time.Duration;
import java.util.Optional;

public interface DistributedLeaseService {

  Optional<DistributedLease> tryAcquire(String key, Duration ttl);
}