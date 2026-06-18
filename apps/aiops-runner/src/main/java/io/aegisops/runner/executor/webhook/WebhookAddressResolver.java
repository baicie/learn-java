package io.aegisops.runner.executor.webhook;

import java.net.InetAddress;
import java.util.List;

public interface WebhookAddressResolver {
  List<InetAddress> resolveAll(String host);
}
