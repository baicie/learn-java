package io.aegisops.workrecord.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class WorkRecordModuleBoundaryTest {

  private final JavaClasses classes =
      new ClassFileImporter().importPackages("io.aegisops.workrecord");

  @Test
  void domainShouldNotDependOnSpringWebOrJdbc() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web..",
            "org.springframework.jdbc..",
            "org.springframework.stereotype..",
            "org.springframework.boot..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void apiShouldNotDependOnJdbcInfrastructureDirectly() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..workrecord.infrastructure.persistence..",
            "org.springframework.jdbc..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void workRecordShouldNotDependOnIncidentAlertOrInspectionRepositories() {
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
