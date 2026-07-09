package io.aegisops.workrecord.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class WorkRecordLayerArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses classes =
      new ClassFileImporter().importPackages("io.aegisops.workrecord");

  @Test
  void domainShouldNotDependOnSpring() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void apiShouldNotDependOnJdbcInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..workrecord.infrastructure.jdbc..", "org.springframework.jdbc..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void applicationShouldNotDependOnApiOrJdbcImplementation() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..workrecord.api..", "..workrecord.infrastructure.jdbc..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void workRecordShouldNotDependOnAlertIncidentInspectionRepositories() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..alert.infrastructure.persistence..",
            "..incident.infrastructure.persistence..",
            "..inspection.infrastructure.persistence..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
