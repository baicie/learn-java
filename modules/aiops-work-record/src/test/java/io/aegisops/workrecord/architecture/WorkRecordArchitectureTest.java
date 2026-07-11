package io.aegisops.workrecord.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.aegisops.workrecord",
    importOptions = ImportOption.DoNotIncludeTests.class)
class WorkRecordArchitectureTest {

  @ArchTest
  static final ArchRule domainMustRemainIndependent =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "..application..",
              "..api..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule applicationMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..api..",
              "..infrastructure..");

  @ArchTest
  static final ArchRule apiMustNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure..");

  @ArchTest
  static final ArchRule jdbcMustOnlyBeUsedByInfrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("..infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "java.sql..");
}
