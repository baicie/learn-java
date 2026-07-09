package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.port.WorkRecordAuditRepository;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkRecordTemplateVersionServiceTest {
  private final WorkRecordTemplateRepository templateRepository =
      Mockito.mock(WorkRecordTemplateRepository.class);
  private final WorkRecordTemplateVersionRepository versionRepository =
      Mockito.mock(WorkRecordTemplateVersionRepository.class);
  private final WorkRecordFieldIndexRepository fieldRepository =
      Mockito.mock(WorkRecordFieldIndexRepository.class);
  private final WorkRecordSchemaService schemaService =
      new WorkRecordSchemaService(
          new io.aegisops.workrecord.application.schema.WorkRecordSchemaParser(
              new ObjectMapper(), new io.aegisops.workrecord.application.schema.WorkRecordSchemaValidator()),
          new io.aegisops.workrecord.application.schema.WorkRecordSchemaValidator(),
          new io.aegisops.workrecord.application.schema.WorkRecordSchemaNormalizer(new ObjectMapper()));
  private final WorkRecordFieldIndexService fieldIndexService =
      new WorkRecordFieldIndexService(fieldRepository);
  private final WorkRecordAuditService auditService =
      new WorkRecordAuditService(
          (WorkRecordAuditRepository) (tenantId, recordId, templateId, resourceType, resourceId, action, actorId, beforeJson, afterJson, detailJson) -> {});
  private final WorkRecordTemplateVersionService service =
      new WorkRecordTemplateVersionService(
          templateRepository, versionRepository, schemaService, fieldIndexService, auditService);

  @Test
  void shouldPublishDraftTemplateIntoVersionAndFieldIndex() {
    String schema =
        """
        {
          "type": "object",
          "properties": {
            "content": {
              "title": "工作内容",
              "x-component": "Textarea",
              "x-work-record": {
                "fieldCode": "content",
                "fieldType": "textarea",
                "listVisible": true,
                "filterable": true,
                "exportable": true
              }
            }
          }
        }
        """;

    WorkRecordTemplate template =
        new WorkRecordTemplate(
            "tpl1",
            "t1",
            "daily",
            "日报",
            null,
            TemplateStatus.DRAFT,
            true,
            null,
            schema,
            "{}",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null);

    WorkRecordTemplateVersion version =
        new WorkRecordTemplateVersion(
            "v1",
            "t1",
            "tpl1",
            1,
            "v1",
            schema,
            "{}",
            "[]",
            "u1",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    when(templateRepository.find("t1", "tpl1")).thenReturn(Optional.of(template));
    when(versionRepository.nextVersionNo("t1", "tpl1")).thenReturn(1);
    when(versionRepository.create(
            Mockito.eq("t1"),
            Mockito.eq("tpl1"),
            Mockito.eq(1),
            Mockito.eq("v1"),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq("u1")))
        .thenReturn(version);

    WorkRecordTemplateVersion published =
        service.publish("t1", new PublishTemplateCommand("tpl1", "v1"), "u1");

    assertThat(published.id()).isEqualTo("v1");
    verify(fieldRepository).createForVersion(Mockito.eq("t1"), Mockito.eq("tpl1"), Mockito.eq("v1"), Mockito.anyList());
    verify(templateRepository).updateCurrentVersion("t1", "tpl1", "v1");
  }
}
