package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SignedDiagnosisGrantProviderSpringWiringTest {
  @Test
  void selectsProductionConstructorWhenTestConstructorAlsoExists() {
    try (var context = new AnnotationConfigApplicationContext()) {
      TestPropertyValues.of("aiops.agent.enabled=true").applyTo(context);
      context.registerBean(ObjectMapper.class);
      context.register(SignedDiagnosisGrantProvider.class);

      context.refresh();

      assertThat(context.getBean(DiagnosisGrantProvider.class))
          .isInstanceOf(SignedDiagnosisGrantProvider.class);
    }
  }
}
