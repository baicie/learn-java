package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.command.TemplatePublishValidationResult;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateUsageRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaDocument;
import io.aegisops.workrecord.application.schema.WorkRecordSchemaNormalizer;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.FormFieldDescriptor;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkRecordTemplateVersionServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      mock(WorkRecordTemplateRepository.class);
  private final WorkRecordTemplateVersionRepository versionRepository =
      mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordTemplateUsageRepository usageRepository =
      mock(WorkRecordTemplateUsageRepository.class);
  private final WorkRecordSchemaService schemaService = mock(WorkRecordSchemaService.class);
  private final WorkRecordSchemaNormalizer schemaNormalizer =
      new WorkRecordSchemaNormalizer(new ObjectMapper());
  private final WorkRecordFieldIndexService fieldIndexService = mock(WorkRecordFieldIndexService.class);
  private final WorkRecordTemplatePublishGuard guard = new WorkRecordTemplatePublishGuard();
  private final WorkRecordAuditService auditService = mock(WorkRecordAuditService.class);

  private final WorkRecordTemplateVersionService service =
      new WorkRecordTemplateVersionService(
          templateRepository,
          versionRepository,
          fieldRepository,
          usageRepository,
          schemaService,
          schemaNormalizer,
          fieldIndexService,
          guard,
          auditService);

  @Test
  void validatePublishShouldReturnErrorsForDisabledTemplate() {
    when(templateRepository.find("t1", "tpl1"))
        .thenReturn(Optional.of(template(TemplateStatus.DISABLED, false, null)));

    TemplatePublishValidationResult result = service.validatePublish("t1", "tpl1");

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(item -> item.contains("disabled template"));
  }

  @Test
  void publishShouldCreateVersionAndSyncFieldIndex() {
    WorkRecordTemplate template = template(TemplateStatus.DRAFT, true, null);
    WorkRecordSchemaDocument document =
        new WorkRecordSchemaDocument(
            1,
            "{\"x-work-record-schema-version\":1}",
            "{}",
            List.of(field("content", FieldType.TEXTAREA)),
            "[]");

    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1",
            "t1",
            "tpl1",
            1,
            "v1",
            document.normalizedSchemaJson(),
            "{}",
            "[]",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(templateRepository.find("t1", "tpl1")).thenReturn(Optional.of(template));
    when(schemaService.prepareForPublish("{}", "{}")).thenReturn(document);
    when(versionRepository.nextVersionNo("t1", "tpl1")).thenReturn(1);
    when(versionRepository.create(any(), any(), anyInt(), any(), any(), any(), any(), any()))
        .thenReturn(version);

    WorkRecordTemplateVersion published =
        service.publish("t1", new PublishTemplateCommand("tpl1", "v1"), "u1");

    assertThat(published.id()).isEqualTo("v1");
    verify(fieldIndexService).createForVersion(eq("t1"), eq("tpl1"), eq("v1"), anyList());
    verify(templateRepository).updateCurrentVersion("t1", "tpl1", "v1");
  }

  @Test
  void publishShouldRejectLockedFieldTypeChangeWhenReferenced() {
    WorkRecordTemplate template = template(TemplateStatus.PUBLISHED, true, "v0");
    WorkRecordSchemaDocument document =
        new WorkRecordSchemaDocument(1, "{}", "{}", List.of(field("priority", FieldType.TEXT)), "[]");

    WorkRecordField previous =
        new WorkRecordField(
            "f1",
            "t1",
            "tpl1",
            "v0",
            "priority",
            "priority",
            FieldType.SELECT,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.priority",
            true,
            true,
            true,
            false,
            1,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(templateRepository.find("t1", "tpl1")).thenReturn(Optional.of(template));
    when(schemaService.prepareForPublish("{}", "{}")).thenReturn(document);
    when(fieldRepository.listByVersion("t1", "v0")).thenReturn(List.of(previous));
    when(usageRepository.countRecordsByTemplateVersion("t1", "v0")).thenReturn(10L);

    assertThatThrownBy(() -> service.publish("t1", new PublishTemplateCommand("tpl1", "v2"), "u1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("fieldType is locked");
  }

  @Test
  void publishShouldUseEffectiveFieldEntriesAsVersionFieldIndexJson() {
    WorkRecordTemplate template = template(TemplateStatus.PUBLISHED, true, "v0");
    WorkRecordSchemaDocument document =
        new WorkRecordSchemaDocument(
            1,
            "{\"x-work-record-schema-version\":1}",
            "{}",
            List.of(field("content", FieldType.TEXTAREA)),
            "[]");

    WorkRecordField removedPreviousField =
        new WorkRecordField(
            "f-old",
            "t1",
            "tpl1",
            "v0",
            "priority",
            "priority",
            FieldType.SELECT,
            false,
            null,
            OptionSource.STATIC,
            null,
            "[]",
            ".properties.priority",
            true,
            true,
            true,
            false,
            1,
            true,
            OffsetDateTime.now(),
            OffsetDateTime.now());

    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1",
            "t1",
            "tpl1",
            2,
            "v2",
            document.normalizedSchemaJson(),
            "{}",
            "[]",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(templateRepository.find("t1", "tpl1")).thenReturn(Optional.of(template));
    when(schemaService.prepareForPublish("{}", "{}")).thenReturn(document);
    when(fieldRepository.listByVersion("t1", "v0")).thenReturn(List.of(removedPreviousField));
    when(usageRepository.countRecordsByTemplateVersion("t1", "v0")).thenReturn(10L);
    when(versionRepository.nextVersionNo("t1", "tpl1")).thenReturn(2);
    when(versionRepository.create(any(), any(), anyInt(), any(), any(), any(), any(), any()))
        .thenReturn(version);

    service.publish("t1", new PublishTemplateCommand("tpl1", "v2"), "u1");

    ArgumentCaptor<String> fieldIndexJsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(versionRepository)
        .create(
            eq("t1"),
            eq("tpl1"),
            eq(2),
            eq("v2"),
            eq(document.normalizedSchemaJson()),
            eq(document.normalizedDesignerJson()),
            fieldIndexJsonCaptor.capture(),
            eq("u1"));

    String captured = fieldIndexJsonCaptor.getValue();
    assertThat(captured).contains("\"fieldCode\":\"priority\"");
    assertThat(captured).contains("\"enabled\":false");
    assertThat(captured).contains("\"fieldCode\":\"content\"");
    assertThat(captured).contains("\"enabled\":true");
  }

  private WorkRecordTemplate template(
      TemplateStatus status, boolean enabled, String currentVersionId) {
    return new WorkRecordTemplate(
        "tpl1",
        "t1",
        "daily",
        "日报",
        null,
        status,
        enabled,
        currentVersionId,
        "{}",
        "{}",
        "u1",
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }

  private FormFieldDescriptor field(String code, FieldType type) {
    return new FormFieldDescriptor(
        code,
        code,
        type,
        false,
        OptionSource.STATIC,
        null,
        "[]",
        ".properties." + code,
        true,
        true,
        true,
        false,
        1);
  }
}