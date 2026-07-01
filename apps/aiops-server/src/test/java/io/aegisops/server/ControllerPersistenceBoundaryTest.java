package io.aegisops.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ControllerPersistenceBoundaryTest {
  @Test
  void controllersMustNotInjectPersistenceClientsDirectly() {
    JavaClasses classes = new ClassFileImporter().importPackages("io.aegisops");

    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(JdbcTemplate.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(DSLContext.class)
            .because("controllers should delegate persistence to application services");

    rule.check(classes);
  }
}
