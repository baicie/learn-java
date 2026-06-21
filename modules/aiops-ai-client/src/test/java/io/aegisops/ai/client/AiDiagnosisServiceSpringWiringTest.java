package io.aegisops.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

class AiDiagnosisServiceSpringWiringTest {
  @ParameterizedTest
  @MethodSource("springComponentsWithMultipleConstructors")
  void componentsWithMultipleConstructorsDeclareExactlyOneAutowiredConstructor(
      Class<?> componentType) {
    long autowiredConstructors =
        Arrays.stream(componentType.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .count();

    assertEquals(1, autowiredConstructors, componentType.getSimpleName());
  }

  private static Stream<Class<?>> springComponentsWithMultipleConstructors() {
    return Stream.of(AiDiagnosisService.class, HttpAiAgentClient.class);
  }
}
