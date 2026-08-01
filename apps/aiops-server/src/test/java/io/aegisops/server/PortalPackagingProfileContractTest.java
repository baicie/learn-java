package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PortalPackagingProfileContractTest {

  @Test
  void serverPomShouldPackageOnlyPortal() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(pom).contains("<id>with-portal</id>");
    assertThat(pom).contains("web/portal/dist");
    assertThat(pom).contains("<id>run-portal-pom</id>");
    assertThat(pom).doesNotContain("<id>with-console</id>");
    assertThat(pom).doesNotContain("web/console/dist");
    assertThat(pom).doesNotContain("<id>run-console-pom</id>");
  }

  @Test
  void portalProfileShouldCopyDistIntoStaticResources() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(pom).contains("${project.build.outputDirectory}/static");
    assertThat(pom).contains("${maven.multiModuleProjectDirectory}/web/portal/dist");
    assertThat(pom).contains("${maven.multiModuleProjectDirectory}/web/portal");
  }
}
