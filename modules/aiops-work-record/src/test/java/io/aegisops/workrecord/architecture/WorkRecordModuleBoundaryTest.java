package io.aegisops.workrecord.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Module-level boundary contract test, complementary to {@link WorkRecordLayerArchitectureTest}.
 * Phase 1 baseline check requires a test in this exact path to enforce that the work-record module
 * stays self-contained and does not reach into alert/incident/inspection persistence packages.
 */
class WorkRecordModuleBoundaryTest {

  private final com.tngtech.archunit.core.domain.JavaClasses classes =
      new ClassFileImporter().importPackages("io.aegisops.workrecord");

  @Test
  void domainShouldNotDependOnApplicationApiOrInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..workrecord.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..workrecord.application..", "..workrecord.api..", "..workrecord.infrastructure..")
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
