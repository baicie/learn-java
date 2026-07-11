package io.aegisops.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Tests for {@link PhaseEnabledCondition}.
 *
 * <p>The condition reads {@code aiops.runtime.phase} from the Spring {@link
 * org.springframework.core.env.Environment} and decides whether to register a bean annotated with
 * {@link PhaseEnabled}. Every (required, actual) combination is exercised via the {@link
 * #phaseMatrix()} source so that a future refactor of the ordinal comparison cannot silently flip a
 * phase.
 *
 * <p>Test beans are declared as nested {@code @PhaseEnabled} classes; {@link
 * StandardAnnotationMetadata} reads the {@code value} attribute directly from the JVM annotation to
 * avoid hand-rolled metadata stubs.
 */
class PhaseEnabledConditionTest {

  private final PhaseEnabledCondition condition = new PhaseEnabledCondition();

  @ParameterizedTest(name = "required={0}, actual={1} -> matches={2}")
  @MethodSource("phaseMatrix")
  void matchesAccordingToPhaseOrdinalComparison(
      RuntimePhase required, RuntimePhase actual, boolean expected) {
    AnnotatedTypeMetadata metadata = metadataFor(required);

    boolean matched = condition.matches(contextWithPhase(actual.propertyName()), metadata);

    assertThat(matched)
        .as(
            "required=%s, actual=%s (env property '%s=%s')",
            required, actual, PhaseEnabledCondition.PHASE_PROPERTY, actual.propertyName())
        .isEqualTo(expected);
  }

  static Stream<Arguments> phaseMatrix() {
    return Stream.of(
        Arguments.of(RuntimePhase.PHASE_0, RuntimePhase.PHASE_0, true),
        Arguments.of(RuntimePhase.PHASE_0, RuntimePhase.PHASE_5, true),
        Arguments.of(RuntimePhase.PHASE_0, RuntimePhase.PHASE_6, true),
        Arguments.of(RuntimePhase.PHASE_1, RuntimePhase.PHASE_0, false),
        Arguments.of(RuntimePhase.PHASE_1, RuntimePhase.PHASE_1, true),
        Arguments.of(RuntimePhase.PHASE_1, RuntimePhase.PHASE_6, true),
        Arguments.of(RuntimePhase.PHASE_3, RuntimePhase.PHASE_2, false),
        Arguments.of(RuntimePhase.PHASE_3, RuntimePhase.PHASE_3, true),
        Arguments.of(RuntimePhase.PHASE_3, RuntimePhase.PHASE_4, true),
        Arguments.of(RuntimePhase.PHASE_5, RuntimePhase.PHASE_4, false),
        Arguments.of(RuntimePhase.PHASE_5, RuntimePhase.PHASE_5, true),
        Arguments.of(RuntimePhase.PHASE_5, RuntimePhase.PHASE_6, true),
        Arguments.of(RuntimePhase.PHASE_6, RuntimePhase.PHASE_5, false),
        Arguments.of(RuntimePhase.PHASE_6, RuntimePhase.PHASE_6, true));
  }

  @Test
  void missingPhasePropertyDefaultsToPhase0AndDisablesLatePhaseBeans() {
    AnnotatedTypeMetadata metadata = metadataFor(RuntimePhase.PHASE_5);

    boolean matched = condition.matches(contextWithPhase(null), metadata);

    assertThat(matched).as("missing aiops.runtime.phase falls back to PHASE_0").isFalse();
  }

  @Test
  void unknownPhaseValueIsTreatedAsPhase0() {
    AnnotatedTypeMetadata metadata = metadataFor(RuntimePhase.PHASE_3);

    boolean matched = condition.matches(contextWithPhase("not-a-phase"), metadata);

    assertThat(matched)
        .as("unknown phase values must not silently promote to a later phase")
        .isFalse();
  }

  @Test
  void acceptsBothWireLabelAndEnumNameFromEnvironment() {
    AnnotatedTypeMetadata metadata = metadataFor(RuntimePhase.PHASE_5);

    assertThat(condition.matches(contextWithPhase("phase5"), metadata)).isTrue();
    assertThat(condition.matches(contextWithPhase("PHASE_5"), metadata)).isTrue();
  }

  private static ConditionContext contextWithPhase(String wireLabel) {
    var environment = new StandardEnvironment();
    if (wireLabel != null) {
      environment
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test", Map.of(PhaseEnabledCondition.PHASE_PROPERTY, wireLabel)));
    }
    return new EnvironmentOnlyConditionContext(environment);
  }

  private static AnnotationMetadata metadataFor(RuntimePhase phase) {
    return AnnotationMetadata.introspect(beanForPhase(phase));
  }

  /** Returns the nested bean class for a given required phase. */
  private static Class<?> beanForPhase(RuntimePhase phase) {
    return switch (phase) {
      case PHASE_0 -> Phase0Bean.class;
      case PHASE_1 -> Phase1Bean.class;
      case PHASE_2 -> Phase2Bean.class;
      case PHASE_3 -> Phase3Bean.class;
      case PHASE_4 -> Phase4Bean.class;
      case PHASE_5 -> Phase5Bean.class;
      case PHASE_6 -> Phase6Bean.class;
    };
  }

  @PhaseEnabled(RuntimePhase.PHASE_0)
  static final class Phase0Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_1)
  static final class Phase1Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_2)
  static final class Phase2Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_3)
  static final class Phase3Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_4)
  static final class Phase4Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_5)
  static final class Phase5Bean {}

  @PhaseEnabled(RuntimePhase.PHASE_6)
  static final class Phase6Bean {}

  /** Minimal {@link ConditionContext} that only exposes the environment. */
  private static final class EnvironmentOnlyConditionContext implements ConditionContext {
    private final org.springframework.core.env.Environment environment;

    EnvironmentOnlyConditionContext(org.springframework.core.env.Environment environment) {
      this.environment = environment;
    }

    @Override
    public org.springframework.core.env.Environment getEnvironment() {
      return environment;
    }

    @Override
    public org.springframework.beans.factory.config.ConfigurableListableBeanFactory
        getBeanFactory() {
      return null;
    }

    @Override
    public org.springframework.core.io.ResourceLoader getResourceLoader() {
      return null;
    }

    @Override
    public ClassLoader getClassLoader() {
      return getClass().getClassLoader();
    }

    @Override
    public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() {
      return null;
    }
  }
}
