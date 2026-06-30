package io.aegisops.common.runtime;

import java.util.Locale;

/**
 * Declares the runtime MVP phase of an AegisOps application process.
 *
 * <p>The value drives {@code /internal/<app>/status} responses and, in subsequent phases, will gate
 * {@code @ConditionalOnProperty(prefix = "aiops.runtime", name = "phase")} beans so that services
 * outside the current MVP phase are not registered in the application context.
 *
 * <p>Phase enumeration maps one-to-one to MVP Phases defined in {@code
 * .agents/skills/aegisops/SKILL.md §14}.
 *
 * <p>The default phase is {@link #PHASE_0}; applications must explicitly declare a phase in their
 * {@code application.yml} (via env var {@code AIOPS_RUNTIME_PHASE}) to opt into a later phase.
 * Conservative default avoids accidentally enabling Phase 5 services on a fresh boot.
 */
public enum RuntimePhase {
  PHASE_0,
  PHASE_1,
  PHASE_2,
  PHASE_3,
  PHASE_4,
  PHASE_5,
  PHASE_6;

  /**
   * Stable wire label exposed by health and status endpoints.
   *
   * <p>Always returns the lowercase phase name without separators (e.g. {@code "phase5"}, not
   * {@code "PHASE_5"} or {@code "phase_5"}) to match Prometheus-style metric labels and to align
   * with the {@code AIOPS_RUNTIME_PHASE} env var convention used in {@code application.yml}.
   */
  public String propertyName() {
    String name = name().toLowerCase(Locale.ROOT);
    StringBuilder builder = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c != '_') {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  /** Parse a wire label (e.g. {@code "phase5"} or {@code "PHASE_5"}) back to an enum constant. */
  public static RuntimePhase fromPropertyName(String value) {
    if (value == null || value.isBlank()) {
      return PHASE_0;
    }
    String upper = value.trim().toUpperCase(Locale.ROOT);
    for (RuntimePhase phase : values()) {
      if (phase.name().equals(upper)
          || phase.propertyName().toUpperCase(Locale.ROOT).equals(upper)) {
        return phase;
      }
    }
    throw new IllegalArgumentException(
        "Unknown runtime phase: " + value + "; expected one of phase0..phase6");
  }
}
