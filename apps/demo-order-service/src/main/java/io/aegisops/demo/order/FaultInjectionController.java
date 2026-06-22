package io.aegisops.demo.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaultInjectionController {

  private final FaultModeService faultModeService;

  public FaultInjectionController(FaultModeService faultModeService) {
    this.faultModeService = faultModeService;
  }

  @GetMapping("/demo/fault/state")
  public FaultStateResponse state() {
    return faultModeService.state();
  }

  @PostMapping("/demo/fault/apply")
  public FaultStateResponse apply(@RequestBody(required = false) FaultMode mode) {
    return faultModeService.apply(mode);
  }

  @PostMapping("/demo/fault/incident")
  public FaultStateResponse incident() {
    return faultModeService.apply(FaultMode.incident());
  }

  @PostMapping("/demo/fault/recover")
  public FaultStateResponse recover() {
    return faultModeService.apply(FaultMode.recover());
  }

  @PostMapping("/demo/fault/reset")
  public FaultStateResponse reset() {
    faultModeService.reset();
    return faultModeService.state();
  }
}
