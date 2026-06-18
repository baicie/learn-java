package io.aegisops.runner.executor.webhook;

import io.aegisops.common.exception.AppException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultWebhookAddressResolver implements WebhookAddressResolver {
  @Override
  public List<InetAddress> resolveAll(String host) {
    try {
      return Arrays.asList(InetAddress.getAllByName(host));
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_HOST_RESOLVE_FAILED", "Webhook host resolution failed");
    }
  }
}
