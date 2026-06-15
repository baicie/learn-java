package io.aegisops.server;

import io.aegisops.tenant.Tenant;
import io.aegisops.tenant.TenantService;
import io.aegisops.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);

  private final TenantService tenantService;
  private final UserService userService;

  public BootstrapInitializer(TenantService tenantService, UserService userService) {
    this.tenantService = tenantService;
    this.userService = userService;
  }

  @Override
  public void run(ApplicationArguments args) {
    Tenant tenant = tenantService.getOrCreateDefaultTenant();
    userService.createAdminIfAbsent(tenant.id(), "admin", "admin123");
    log.info("Bootstrap completed. Default login: admin / admin123");
  }
}
