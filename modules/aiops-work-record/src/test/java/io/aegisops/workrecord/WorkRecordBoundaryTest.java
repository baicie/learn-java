package io.aegisops.workrecord;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作记录模块架构边界测试。
 *
 * <p>防止工作记录模块越界依赖 Incident / Alert / Inspection 等业务域，保持模块独立可复用。
 */
@AnalyzeClasses(
    packages = "io.aegisops.workrecord",
    importOptions = ImportOption.DoNotIncludeTests.class)
class WorkRecordBoundaryTest {

  @ArchTest
  static final ArchRule controllers_should_not_depend_on_jdbc_directly =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackages("org.springframework.jdbc..");

  @ArchTest
  static final ArchRule domain_should_not_depend_on_business_modules =
      classes()
          .that()
          .resideInAPackage("..workrecord..")
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackages(
              "io.aegisops.incident..",
              "io.aegisops.alert..",
              "io.aegisops.inspection..",
              "io.aegisops.runbook..",
              "io.aegisops.execution..",
              "io.aegisops.zabbixadapter..");
}
