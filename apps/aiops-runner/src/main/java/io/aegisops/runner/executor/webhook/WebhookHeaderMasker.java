package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.WebhookJson;
import io.aegisops.execution.dto.WebhookConnectorRecord;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WebhookHeaderMasker {
  private final WebhookJson json;

  public WebhookHeaderMasker(ObjectMapper objectMapper) {
    this.json = new WebhookJson(objectMapper);
  }

  public Map<String, String> mask(WebhookConnectorRecord connector, Map<String, String> headers) {
    Set<String> sensitive =
        json.readStringList(connector.sensitiveHeadersJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (sensitive.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
        result.put(entry.getKey(), "***");
      } else {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }
}
