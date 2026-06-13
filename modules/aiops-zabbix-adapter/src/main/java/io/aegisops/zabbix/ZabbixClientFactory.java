package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ZabbixClientFactory {
    private final ObjectMapper objectMapper;

    public ZabbixClientFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ZabbixClient create(ZabbixConfig config) {
        return new DefaultZabbixClient(config, objectMapper);
    }
}
