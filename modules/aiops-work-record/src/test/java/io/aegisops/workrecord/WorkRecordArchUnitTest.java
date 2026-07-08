package io.aegisops.workrecord;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

/**
 * aiops-work-record 模块的 ArchUnit 边界守卫。
 *
 * <p>规则与 {@code .agents/skills/aegisops/references/module-package-conventions.md §7.3} 对齐，
 * 五个测试覆盖：domain 纯净、api 不直连持久层、controller / repository 包归属、模块间零依赖。
 *
 * <p>每个强约束模块都应平行复制本类并替换 {@code workrecord}。
 */
class WorkRecordArchUnitTest {

  private static final String BASE = "io.aegisops.workrecord";

  private static JavaClasses workRecordClasses() {
    return new ClassFileImporter().importPackages(BASE);
  }

  @Test
  void workRecordDomainMustNotDependOnWebOrJdbcOrInfra() {
    JavaClasses classes = workRecordClasses();

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(BASE + ".domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.jdbc..",
                "org.jooq..",
                BASE + ".infrastructure..",
                BASE + ".application..");
    rule.check(classes);
  }

  @Test
  void workRecordApiMustNotDependOnJdbcOrJooq() {
    JavaClasses classes = workRecordClasses();

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(BASE + ".api..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(JdbcTemplate.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(org.jooq.DSLContext.class)
            .because("controllers should delegate persistence to application services");
    rule.check(classes);
  }

  @Test
  void workRecordControllersMustLiveInApiPackage() {
    JavaClasses classes = workRecordClasses();

    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .resideInAPackage(BASE + ".api..");
    rule.check(classes);
  }

  @Test
  void workRecordRepositoriesMustLiveInInfrastructurePersistence() {
    JavaClasses classes = workRecordClasses();

    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(Repository.class)
            .or()
            .haveSimpleNameEndingWith("Repository")
            .or()
            .haveSimpleNameEndingWith("Dao")
            .should()
            .resideInAPackage(BASE + ".infrastructure.persistence..");
    rule.check(classes);
  }

  @Test
  void workRecordMustNotDependOnAlertOrIncidentOrInspection() {
    JavaClasses classes = workRecordClasses();

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(BASE + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.aegisops.alert..",
                "io.aegisops.incident..",
                "io.aegisops.inspection..",
                "io.aegisops.evidence..",
                "io.aegisops.rca..",
                "io.aegisops.runbook..",
                "io.aegisops.execution..");
    rule.check(classes);
  }
}