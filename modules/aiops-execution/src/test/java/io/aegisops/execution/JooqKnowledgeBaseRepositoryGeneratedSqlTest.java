package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.KnowledgeBaseChunkCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseDocumentCreateCommand;
import io.aegisops.execution.dto.KnowledgeBaseSearchLogCreateCommand;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JooqKnowledgeBaseRepositoryGeneratedSqlTest {
  @Test
  void upsertDocumentUsesKbDocumentTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqKnowledgeBaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.upsertDocument(
        new KnowledgeBaseDocumentCreateCommand(
            "kbd_1", "tenant_1", "incident_case", "icase_1", "title", "indexed", "{}"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("kb_document"), "expected kb_document, got: " + sql);
    assertTrue(sql.contains("source_type"), "expected source_type, got: " + sql);
  }

  @Test
  void createChunkUsesKbChunkTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqKnowledgeBaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createChunk(
        new KnowledgeBaseChunkCreateCommand(
            "kbc_1",
            "tenant_1",
            "kbd_1",
            1,
            "incident_case",
            "icase_1",
            "title",
            "content",
            "hash",
            10,
            "[]",
            "{}",
            "indexed"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("kb_chunk"), "expected kb_chunk, got: " + sql);
    assertTrue(sql.contains("embedding"), "expected embedding, got: " + sql);
  }

  @Test
  void createSearchLogUsesKbSearchLogTable() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    var repository =
        new JooqKnowledgeBaseRepository(
            DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.createSearchLog(
        new KnowledgeBaseSearchLogCreateCommand(
            "kbs_1", "tenant_1", "redis timeout", "[\"incident_case\"]", "[]", 5, 1, "alice"));

    String sql = sqlRef.get();
    if (sql == null) {
      throw new AssertionError("SQL was not captured");
    }
    sql = sql.toLowerCase();

    assertTrue(sql.contains("kb_search_log"), "expected kb_search_log, got: " + sql);
    assertTrue(sql.contains("result_count"), "expected result_count, got: " + sql);
  }
}
