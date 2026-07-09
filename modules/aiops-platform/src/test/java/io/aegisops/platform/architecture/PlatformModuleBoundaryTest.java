package io.aegisops.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class PlatformModuleBoundaryTest {

  private final JavaClasses classes =
      new ClassFileImporter().importPackages("io.aegisops.platform");

  @Test
  void platformShouldNotDependOnWorkRecord() {
    noClasses()
        .that()
        .resideInAPackage("..platform..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..workrecord..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void dictionaryShouldNotDependOnCalendarOrWorkRecord() {
    noClasses()
        .that()
        .resideInAPackage("..platform.dictionary..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..platform.calendar..", "..workrecord..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void calendarShouldNotDependOnDictionaryOrWorkRecord() {
    noClasses()
        .that()
        .resideInAPackage("..platform.calendar..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..platform.dictionary..", "..workrecord..")
        .allowEmptyShould(true)
        .check(classes);
  }
}
