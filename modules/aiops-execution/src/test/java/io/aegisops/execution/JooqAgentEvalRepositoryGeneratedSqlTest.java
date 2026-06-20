package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.AgentEvalCaseCreateCommand;
import io.aegisops.execution.dto.AgentEvalCaseResultCreateCommand;
import io.aegisops.execution.dto.AgentEvalDatasetCreateCommand;
import io.aegisops.execution.dto.AgentEvalRunCreateCommand;
import io.aegisops.execution.dto.AgentPromptProfileCreateCommand;
import java.util.ArrayList;
import java.util.List;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqAgentEvalRepositoryGeneratedSqlTest {
  @Test
  void createDatasetAndCaseUseEvalTables() {
    List<String> capturedSql = new ArrayList<>();

    MockDataProvider provider =
        context -> {
          capturedSql.add(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqAgentEvalRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createDataset(
        new AgentEvalDatasetCreateCommand(
            "aeds_1", "tenant_1", "dataset", "desc", "draft", "alice"));

    repository.createCase(
        new AgentEvalCaseCreateCommand(
            "aec_1",
            "tenant_1",
            "aeds_1",
            "manual",
            null,
            "inc_1",
            "title",
            "high",
            "context",
            "redis timeout",
            "[\"redis\"]",
            "[\"restart\"]",
            "[]",
            "[]",
            true,
            "alice"));

    String sql = String.join("\n", capturedSql).toLowerCase();

    assertTrue(sql.contains("agent_eval_dataset"), "expected agent_eval_dataset, got: " + sql);
    assertTrue(sql.contains("agent_eval_case"), "expected agent_eval_case, got: " + sql);
  }

  @Test
  void createPromptRunResultUseEvalTables() {
    List<String> capturedSql = new ArrayList<>();

    MockDataProvider provider =
        context -> {
          capturedSql.add(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqAgentEvalRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createPromptProfile(
        new AgentPromptProfileCreateCommand(
            "appf_1",
            "tenant_1",
            "default",
            "v1",
            "active",
            "system prompt",
            "diagnosis template",
            "{}",
            "alice"));

    repository.createRun(
        new AgentEvalRunCreateCommand(
            "aer_1",
            "tenant_1",
            "aeds_1",
            "appf_1",
            "mock",
            "running",
            0,
            0,
            0,
            0.0,
            null,
            "alice"));

    repository.createCaseResult(
        new AgentEvalCaseResultCreateCommand(
            "aecr_1",
            "tenant_1",
            "aer_1",
            "aec_1",
            "aid_1",
            "summary",
            "root cause",
            "recommendation",
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            true,
            "{}"));

    String sql = String.join("\n", capturedSql).toLowerCase();

    assertTrue(sql.contains("agent_prompt_profile"), "expected agent_prompt_profile, got: " + sql);
    assertTrue(sql.contains("agent_eval_run"), "expected agent_eval_run, got: " + sql);
    assertTrue(
        sql.contains("agent_eval_case_result"), "expected agent_eval_case_result, got: " + sql);
  }
}
