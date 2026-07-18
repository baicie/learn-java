package io.aegisops.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class OperationsIngestAuthorizationTest {

  @Test
  void ingestionEndpointsRequireDedicatedDatasourcePermission() {
    PreAuthorize annotation = OperationsIngestController.class.getAnnotation(PreAuthorize.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("hasAuthority('datasource:ingest')");
  }
}
