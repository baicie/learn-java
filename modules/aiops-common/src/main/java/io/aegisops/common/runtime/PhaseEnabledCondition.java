package io.aegisops.common.runtime;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring {@link org.springframework.context.annotation.Condition} backing {@link PhaseEnabled}.
 *
 * <p>Reads {@code aiops.runtime.phase} from the {@link org.springframework.core.env.Environment}
 * exposed by {@code ConditionContext} and compares it to the {@link PhaseEnabled#value()} declared
 * on the annotated bean. The bean is registered when the configured phase ordinal is {@code >=} the
 * required phase ordinal.
 *
 * <p>Reading the phase through the environment (rather than injecting {@link RuntimeProperties}) is
 * deliberate: Spring evaluates conditions very early in the context lifecycle, before configuration
 * properties binding finishes, so a bean dependency here would create a cycle. Falling back to
 * {@link RuntimePhase#PHASE_0} when the property is absent matches the conservative default in
 * {@link RuntimeProperties}.
 */
public class PhaseEnabledCondition implements org.springframework.context.annotation.Condition {

  static final String PHASE_PROPERTY = "aiops.runtime.phase";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    RuntimePhase required = requiredPhase(metadata);
    if (required == null) {
      // Annotation missing or unparseable; defer to the rest of the Spring validation pipeline.
      return true;
    }
    RuntimePhase actual = readActualPhase(context);
    return actual.ordinal() >= required.ordinal();
  }

  private static RuntimePhase requiredPhase(AnnotatedTypeMetadata metadata) {
    var attributes = metadata.getAnnotationAttributes(PhaseEnabled.class.getName());
    if (attributes == null) {
      return null;
    }
    Object value = attributes.get("value");
    if (value instanceof RuntimePhase phase) {
      return phase;
    }
    return null;
  }

  private static RuntimePhase readActualPhase(ConditionContext context) {
    var environment = context.getEnvironment();
    if (environment == null) {
      return RuntimePhase.PHASE_0;
    }
    String wireLabel = environment.getProperty(PHASE_PROPERTY);
    if (wireLabel == null || wireLabel.isBlank()) {
      return RuntimePhase.PHASE_0;
    }
    try {
      return RuntimePhase.fromPropertyName(wireLabel);
    } catch (IllegalArgumentException e) {
      // Surface the underlying problem but do not hard-fail condition evaluation here;
      // ConfigurationProperties binding or actuator probes will produce a clearer error.
      return RuntimePhase.PHASE_0;
    }
  }
}
