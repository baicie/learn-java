package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SignedDiagnosisGrantProviderSpringWiringTest {
  @Test
  void selectsProductionConstructorWhenTestConstructorAlsoExists(@TempDir Path tempDir)
      throws Exception {
    Path privateKeyFile = tempDir.resolve("task-grant-private.pem");
    byte[] encoded =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate().getEncoded();
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    Files.writeString(
        privateKeyFile, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");

    try (var context = new AnnotationConfigApplicationContext()) {
      TestPropertyValues.of(
              "aiops.agent.enabled=true", "aiops.agent.grant.private-key-file=" + privateKeyFile)
          .applyTo(context);
      context.registerBean(ObjectMapper.class);
      context.register(SignedDiagnosisGrantProvider.class);

      context.refresh();

      assertThat(context.getBean(DiagnosisGrantProvider.class))
          .isInstanceOf(SignedDiagnosisGrantProvider.class);
    }
  }
}
