package io.aegisops.asset;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class AssetArchUnitTest {

  private final JavaClasses classes = new ClassFileImporter().importPackages("io.aegisops.asset");

  @Test
  void controllersMustResideInApiPackage() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .resideInAPackage("..api..")
        .check(classes);
  }

  @Test
  void applicationServicesMustResideInApplicationPackage() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Service")
        .should()
        .resideInAPackage("..application..")
        .check(classes);
  }

  @Test
  void repositoriesMustResideInInfrastructurePersistencePackage() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Repository")
        .should()
        .resideInAPackage("..infrastructure.persistence..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void domainMustNotDependOnFrameworkOrPersistenceTypes() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "org.springframework.jdbc..", "org.jooq..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void apiMustNotDependOnPersistenceTypes() {
    noClasses()
        .that()
        .resideInAPackage("..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.jdbc..", "org.jooq..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
