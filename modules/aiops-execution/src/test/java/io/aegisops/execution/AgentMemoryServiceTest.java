package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.AgentMemoryCreateCommand;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import io.aegisops.execution.dto.AgentMemoryEventCreateCommand;
import io.aegisops.execution.dto.AgentMemoryRecord;
import io.aegisops.execution.dto.AgentMemorySearchRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentMemoryServiceTest {
  @Test
  void createAndSearchMemory() {
    FakeAgentMemoryRepository repository = new FakeAgentMemoryRepository();
    AgentMemoryJson json = new AgentMemoryJson(new ObjectMapper());

    AgentMemoryService service =
        new AgentMemoryService(repository, new AgentMemoryPolicy(), json, new AgentMemoryScorer());

    var created =
        service.createInternal(
            new AgentMemoryCreateRequest(
                "tenant_1",
                "tenant",
                null,
                "root_cause_pattern",
                "diagnosis",
                "inc_1",
                "Redis timeout pattern",
                "Order service redis timeout caused 5xx.",
                List.of("redis", "timeout"),
                0.8,
                null,
                "agent"));

    assertEquals("active", created.status());

    var result =
        service.searchInternal(
            new AgentMemorySearchRequest(
                "tenant_1",
                "redis timeout",
                "tenant",
                null,
                List.of("root_cause_pattern"),
                List.of("redis"),
                5,
                "agent"));

    assertEquals(1, result.results().size());
    assertTrue(result.results().get(0).score() > 0);
  }

  @Test
  void archiveMemory() {
    FakeAgentMemoryRepository repository = new FakeAgentMemoryRepository();
    AgentMemoryService service =
        new AgentMemoryService(
            repository,
            new AgentMemoryPolicy(),
            new AgentMemoryJson(new ObjectMapper()),
            new AgentMemoryScorer());

    var created =
        service.createInternal(
            new AgentMemoryCreateRequest(
                "tenant_1",
                "tenant",
                null,
                "safety_note",
                "diagnosis",
                "inc_1",
                "Do not restart",
                "Do not restart during data migration.",
                List.of("safety"),
                0.9,
                null,
                "agent"));

    var archived = service.archive("tenant_1", created.id());

    assertEquals("archived", archived.status());
  }

  private static class FakeAgentMemoryRepository implements AgentMemoryRepository {
    AgentMemoryRecord memory;
    final List<AgentMemoryEventCreateCommand> events = new ArrayList<>();

    @Override
    public void create(AgentMemoryCreateCommand command) {
      memory =
          new AgentMemoryRecord(
              command.id(),
              command.tenantId(),
              command.scopeType(),
              command.scopeId(),
              command.memoryType(),
              command.sourceType(),
              command.sourceId(),
              command.title(),
              command.content(),
              command.tagsJson(),
              command.confidence(),
              command.status(),
              command.createdBy(),
              command.expiresAt(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public Optional<AgentMemoryRecord> find(String tenantId, String memoryId) {
      return Optional.ofNullable(memory)
          .filter(item -> item.tenantId().equals(tenantId) && item.id().equals(memoryId));
    }

    @Override
    public List<AgentMemoryRecord> listActiveCandidates(ListActiveMemoryParams params) {
      if (memory == null || !"active".equals(memory.status())) {
        return List.of();
      }
      return List.of(memory);
    }

    @Override
    public boolean archive(String tenantId, String memoryId) {
      if (memory == null || !memory.id().equals(memoryId)) {
        return false;
      }
      memory =
          new AgentMemoryRecord(
              memory.id(),
              memory.tenantId(),
              memory.scopeType(),
              memory.scopeId(),
              memory.memoryType(),
              memory.sourceType(),
              memory.sourceId(),
              memory.title(),
              memory.content(),
              memory.tagsJson(),
              memory.confidence(),
              "archived",
              memory.createdBy(),
              memory.expiresAt(),
              memory.createdAt(),
              OffsetDateTime.now());
      return true;
    }

    @Override
    public void createEvent(AgentMemoryEventCreateCommand command) {
      events.add(command);
    }
  }
}
