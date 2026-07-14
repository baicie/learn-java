package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.FieldPolicyRepository;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FieldPolicyServiceTest {
  @Test
  void removesHiddenFieldsAndRejectsUnauthorizedWrites() {
    FieldPolicyRepository repository = mock(FieldPolicyRepository.class);
    when(repository.listByVersion("tenant-1", "version-1"))
        .thenReturn(
            List.of(
                new FieldPolicy(
                    "version-1",
                    "salary",
                    List.of("record_admin"),
                    List.of("system_admin"),
                    FieldPolicy.MaskMode.FULL)));
    var service =
        new FieldPolicyService(
            repository, mock(WorkRecordFieldIndexRepository.class), new ObjectMapper());
    UserPrincipal user =
        new UserPrincipal(
            new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
            Set.of("normal_user"),
            Set.of(),
            Map.of());

    assertThat(
            service.filterReadableJson(
                "tenant-1", "version-1", "{\"salary\":10000,\"summary\":\"done\"}", user))
        .doesNotContain("salary")
        .contains("summary");
    assertThatThrownBy(
            () ->
                service.requireWritablePatch(
                    "tenant-1", "version-1", "{\"salary\":10000}", "{\"salary\":20000}", user))
        .isInstanceOf(AccessDeniedException.class);
  }
}
