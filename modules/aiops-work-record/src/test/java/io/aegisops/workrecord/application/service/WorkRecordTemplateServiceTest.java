package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.CopyTemplateCommand;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkRecordTemplateServiceTest {
  private final WorkRecordTemplateRepository repository = mock(WorkRecordTemplateRepository.class);
  private final WorkRecordTemplateUsageRepository usageRepository =
      mock(WorkRecordTemplateUsageRepository.class);
  private final WorkRecordSchemaService schemaService = mock(WorkRecordSchemaService.class);
  private final WorkRecordAuditService auditService = mock(WorkRecordAuditService.class);
  private final WorkRecordAuditSnapshots auditSnapshots =
      new WorkRecordAuditSnapshots(new ObjectMapper());
  private final WorkRecordTemplateService service =
      new WorkRecordTemplateService(
          repository, usageRepository, schemaService, auditService, auditSnapshots);

  @Test
  void shouldRejectDuplicatedTemplateCodeOnCreate() {
    when(repository.findByCode("t1", "daily"))
        .thenReturn(Optional.of(template("tpl1", TemplateStatus.DRAFT, true)));

    assertThatThrownBy(
            () ->
                service.create(
                    "t1", new CreateTemplateCommand("daily", "日报", null, "{}", "{}"), "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("template code already exists");
  }

  @Test
  void shouldRejectEditArchivedTemplate() {
    when(repository.find("t1", "tpl1"))
        .thenReturn(Optional.of(template("tpl1", TemplateStatus.ARCHIVED, false)));

    assertThatThrownBy(
            () -> service.update("t1", "tpl1", new UpdateTemplateCommand("新名称", null), "u1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("archived template cannot be edited");
  }

  @Test
  void shouldRejectArchiveReferencedTemplate() {
    when(repository.find("t1", "tpl1"))
        .thenReturn(Optional.of(template("tpl1", TemplateStatus.PUBLISHED, true)));
    when(usageRepository.countRecordsByTemplate("t1", "tpl1")).thenReturn(1L);

    assertThatThrownBy(() -> service.archive("t1", "tpl1", "u1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("referenced by records");
  }

  @Test
  void shouldCopyTemplateAsNewDraft() {
    WorkRecordTemplate source = template("tpl1", TemplateStatus.PUBLISHED, true);
    WorkRecordTemplate copied = template("tpl2", TemplateStatus.DRAFT, true);

    when(repository.find("t1", "tpl1")).thenReturn(Optional.of(source));
    when(repository.findByCode("t1", "daily_copy")).thenReturn(Optional.empty());
    when(repository.create(any(), any(), any())).thenReturn(copied);

    service.copy("t1", new CopyTemplateCommand("tpl1", "daily_copy", "日报副本", "copy"), "u1");

    verify(repository).create(eq("t1"), any(CreateTemplateCommand.class), eq("u1"));
  }

  @Test
  void shouldArchiveUnreferencedTemplateAndReturnArchivedTemplate() {
    WorkRecordTemplate active = template("tpl1", TemplateStatus.PUBLISHED, true);
    WorkRecordTemplate archived = template("tpl1", TemplateStatus.ARCHIVED, false);

    when(repository.find("t1", "tpl1"))
        .thenReturn(Optional.of(active))
        .thenReturn(Optional.of(archived));
    when(usageRepository.countRecordsByTemplate("t1", "tpl1")).thenReturn(0L);

    WorkRecordTemplate result = service.archive("t1", "tpl1", "u1");

    assertThat(result.status()).isEqualTo(TemplateStatus.ARCHIVED);
    assertThat(result.enabled()).isFalse();
    verify(repository).archive("t1", "tpl1");
  }

  private WorkRecordTemplate template(String id, TemplateStatus status, boolean enabled) {
    return new WorkRecordTemplate(
        id,
        "t1",
        id + "_code",
        id + "_name",
        null,
        status,
        enabled,
        status == TemplateStatus.PUBLISHED ? "v1" : null,
        "{}",
        "{}",
        "u1",
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }
}
