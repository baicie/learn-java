package io.aegisops.common.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties bound from the {@code aiops.runtime.*} key prefix.
 *
 * <p>Currently the only knob is {@link #phase()}; future phases may add further fields such as
 * {@code buildVersion} or {@code releaseTag} but must not introduce behaviour-changing defaults in
 * MVP.
 *
 * <p>Apps register this properties class via their {@code @SpringBootApplication} entry point:
 *
 * <ul>
 *   <li>{@code aegisops-app} registers it implicitly through {@code @ConfigurationPropertiesScan}
 *   <li>{@code aiops-runner} declares
 *       {@code @EnableConfigurationProperties(RuntimeProperties.class)}
 * </ul>
 */
@ConfigurationProperties(prefix = "aiops.runtime")
public record RuntimeProperties(RuntimePhase phase) {

  public RuntimeProperties {
    if (phase == null) {
      phase = RuntimePhase.PHASE_0;
    }
  }

  /** Default phase when no configuration is supplied. */
  public static RuntimePhase defaultPhase() {
    return RuntimePhase.PHASE_0;
  }
}
