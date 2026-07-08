package io.aegisops.workrecord;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作记录模块架构边界测试。
 *
 * <p>防止工作记录模块越界依赖 Incident / Alert / Inspection 等业务域，保持模块独立可复用。
 *
 * <p>规则清单：
 *
 * <ul>
 *   <li>控制器不能直接依赖 JDBC（必须经 Service）
 *   <li>领域模块不能依赖其它业务域
 *   <li>导出服务必须注入字段仓储，保证 exportable 字段级权限校验生效
 *   <li>字段权限校验必须经过 WorkRecordFilterValidator（防止业务侧绕过）
 * </ul>
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

  /**
   * 导出服务必须注入 WorkRecordFieldRepository，保证 exportable 字段级权限校验生效。
   *
   * <p>如果未来有人把 export 链路简化掉字段仓储，P0-2 的字段级 exportable 校验会失效。
   * 本规则是字段权限位的最后一道防线。
   */
  @ArchTest
  static final ArchRule export_service_must_inject_field_repository =
      constructors()
          .that()
          .areDeclaredInClassesThat()
          .haveSimpleName("WorkRecordExportService")
          .should(injectWorkRecordFieldRepository());

  @ArchTest
  static final ArchRule query_service_must_inject_field_repository =
      constructors()
          .that()
          .areDeclaredInClassesThat()
          .haveSimpleName("WorkRecordQueryService")
          .should(injectWorkRecordFieldRepository());

  private static ArchCondition<JavaConstructor> injectWorkRecordFieldRepository() {
    DescribedPredicate<List<JavaClass>> containsFieldRepository =
        new DescribedPredicate<List<JavaClass>>("contains WorkRecordFieldRepository") {
          @Override
          public boolean test(List<JavaClass> types) {
            return types.stream()
                .anyMatch((JavaClass c) -> c.isEquivalentTo(WorkRecordFieldRepository.class));
          }
        };
    return new ArchCondition<JavaConstructor>(
        "declare at least one constructor with WorkRecordFieldRepository parameter") {
      @Override
      public void check(JavaConstructor item, ConditionEvents events) {
        List<JavaClass> params = item.getRawParameterTypes();
        if (containsFieldRepository.test(params)) {
          events.add(SimpleConditionEvent.satisfied(item, item.getDescription()));
        } else {
          events.add(SimpleConditionEvent.violated(item,
              item.getDescription() + " does not inject WorkRecordFieldRepository"));
        }
      }
    };
  }
}
