package io.aegisops.demo.order;

import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ZabbixMetricsController {

  private final FaultModeService faultModeService;

  public ZabbixMetricsController(FaultModeService faultModeService) {
    this.faultModeService = faultModeService;
  }

  @GetMapping(value = "/zabbix/health-status", produces = MediaType.TEXT_PLAIN_VALUE)
  public String healthStatus() {
    return faultModeService.isHealthy() ? "1" : "0";
  }

  @GetMapping(value = "/zabbix/order-create-time", produces = MediaType.TEXT_PLAIN_VALUE)
  public String orderCreateTime() {
    return format(faultModeService.simulatedOrderLatencySeconds());
  }

  @GetMapping(value = "/zabbix/cpu-util", produces = MediaType.TEXT_PLAIN_VALUE)
  public String cpuUtil() {
    return format(faultModeService.simulatedCpuUtil());
  }

  @GetMapping(value = "/zabbix/memory-util", produces = MediaType.TEXT_PLAIN_VALUE)
  public String memoryUtil() {
    return format(faultModeService.simulatedMemoryUtil());
  }

  @GetMapping(value = "/zabbix/load-avg", produces = MediaType.TEXT_PLAIN_VALUE)
  public String loadAvg() {
    return format(faultModeService.simulatedLoadAvg());
  }

  @GetMapping(value = "/zabbix/error-count", produces = MediaType.TEXT_PLAIN_VALUE)
  public String errorCount() {
    return String.valueOf(faultModeService.errorCount());
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
