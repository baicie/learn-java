package io.aegisops.runner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.RollbackRepository;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit guard that locks the {@code apps/aiops-runner → io.aegisops.execution.*Repository}
 * boundary in place.
 *
 * <p>Background: the runner is a separate Maven application. It may freely depend on {@code
 * io.aegisops.execution} for read/write work, but it MUST go through the {@code
 * *ApplicationService} contracts so the JOOQ types stay inside that module. The {@link
 * RunnerExecutionService} rewrite introduced the {@code ExecutionApplicationService} / {@code
 * RollbackApplicationService} surface precisely to make this rule enforceable.
 *
 * <p>Scope: only the {@code RunnerExecutionService} class is checked — it is the sole entry point
 * in the runner service layer that orchestrates cross-module writes. All other runner classes
 * ({@code io.aegisops.runner.executor..}) are internal implementation details and are allowed to
 * hold JOOQ references. The ApplicationService contracts ({@code ExecutionApplicationService},
 * {@code RollbackApplicationService}) are the only cross-module surface.
 *
 * <p>Test sources are excluded on purpose — fakes need to talk to repositories only to model the
 * old boundary for a transition period; enforcing the rule on tests would force us to maintain a
 * parallel ApplicationService fake hierarchy. The {@code FakeExecutionApplicationService} / {@code
 * FakeRollbackApplicationService} that already exist in {@code RunnerExecutionServiceTest} are the
 * long-term path for tests too.
 */
class RunnerArchUnitGuardTest {

  private final JavaClasses runnerMainClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.aegisops.runner", "io.aegisops.runner.executor");

  @Test
  void runnerServiceLayerMustNotAccessExecutionRepository() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.aegisops.runner")
            .and()
            .haveSimpleName("RunnerExecutionService")
            .should()
            .accessClassesThat()
            .haveFullyQualifiedName(ExecutionRepository.class.getName());

    rule.check(runnerMainClasses);
  }

  @Test
  void runnerServiceLayerMustNotAccessRollbackRepository() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("io.aegisops.runner")
            .and()
            .haveSimpleName("RunnerExecutionService")
            .should()
            .accessClassesThat()
            .haveFullyQualifiedName(RollbackRepository.class.getName());

    rule.check(runnerMainClasses);
  }
}
