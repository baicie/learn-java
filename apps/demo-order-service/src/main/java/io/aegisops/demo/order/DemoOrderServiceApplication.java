package io.aegisops.demo.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Demo-only business-side process that simulates an order service for the Phase Z9 end-to-end demo
 * flow.
 *
 * <p>This application is intentionally <strong>not</strong> part of the MVP production topology.
 * Per ADR 0002 (docs/adr/0002-mvp-fourth-app-justification.md) it must only run under the {@code
 * demo} Spring profile, which is opt-in for dev / staging / smoke-test environments.
 *
 * <p>Production {@code mvn verify} does not activate the {@code demo} profile, so this main refuses
 * to start and the bean graph never gets built.
 */
@SpringBootApplication
public class DemoOrderServiceApplication {

  private static final Logger log = LoggerFactory.getLogger(DemoOrderServiceApplication.class);

  public static void main(String[] args) {
    Environment env =
        SpringApplication.run(DemoOrderServiceApplication.class, args).getEnvironment();
    String[] activeProfiles = env.getActiveProfiles();
    if (!containsProfile(activeProfiles, "demo")) {
      log.error(
          "DemoOrderServiceApplication refusing to start without the 'demo' Spring profile."
              + " Active profiles were: {}. Start with --spring.profiles.active=demo."
              + " See ADR 0002 (docs/adr/0002-mvp-fourth-app-justification.md) for context.",
          activeProfiles.length == 0 ? "<none>" : String.join(",", activeProfiles));
      System.exit(1);
    }
    log.info(
        "DemoOrderServiceApplication started under profiles: {}", String.join(",", activeProfiles));
  }

  /** Pure helper used by tests to keep the profile guard testable without invoking {@code main}. */
  static boolean containsProfile(String[] activeProfiles, String target) {
    if (activeProfiles == null) {
      return false;
    }
    for (String profile : activeProfiles) {
      if (target.equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }
}
