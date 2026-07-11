package io.aegisops.security;

public interface DistributedLease extends AutoCloseable {

  String key();

  @Override
  void close();
}