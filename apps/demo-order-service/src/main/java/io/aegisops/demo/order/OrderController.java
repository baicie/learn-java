package io.aegisops.demo.order;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);

  private final FaultModeService faultModeService;

  public OrderController(FaultModeService faultModeService) {
    this.faultModeService = faultModeService;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    if (!faultModeService.isHealthy()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              Map.of(
                  "status", "DOWN",
                  "service", "order-service",
                  "timestamp", Instant.now().toString()));
    }

    return ResponseEntity.ok(
        Map.of(
            "status", "UP",
            "service", "order-service",
            "timestamp", Instant.now().toString()));
  }

  @PostMapping("/api/order/create")
  public ResponseEntity<OrderCreateResponse> createOrder(
      @Valid @RequestBody OrderCreateRequest request) {
    long startedAt = System.nanoTime();

    faultModeService.sleepIfSlowApiEnabled();

    if (faultModeService.isErrorLogEnabled()) {
      long count = faultModeService.incrementErrorCount(1);
      log.error(
          "AegisOps demo fault: Timeout while creating order, skuId={}, quantity={}, errorCount={}",
          request.skuId(),
          request.quantity(),
          count);
    }

    long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;

    if (!faultModeService.isHealthy()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              new OrderCreateResponse(
                  null, "FAILED", latencyMs, "order-service is unhealthy in demo fault mode"));
    }

    return ResponseEntity.ok(
        new OrderCreateResponse(
            "ord_" + UUID.randomUUID(), "CREATED", latencyMs, "order created successfully"));
  }
}
