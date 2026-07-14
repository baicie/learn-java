package io.aegisops.workrecord.application.service;

import io.aegisops.workrecord.application.command.ExcelImportJobRequest;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import java.io.InputStream;
import org.springframework.stereotype.Service;

@Service
public class ExcelImportWorkbookReader {
  private final WorkRecordFieldIndexRepository fields;
  private final ExcelImportParser parser;

  public ExcelImportWorkbookReader(
      WorkRecordFieldIndexRepository fields, ExcelImportParser parser) {
    this.fields = fields;
    this.parser = parser;
  }

  public ExcelImportParser.ParseResult read(
      String tenantId, ExcelImportJobRequest request, InputStream input) {
    return parser.parseAll(
        input,
        fields.listByVersion(tenantId, request.templateVersionId()),
        new ExcelImportParser.ImportDefaults(
            request.defaultStatus(), request.defaultOwnerId(), request.defaultRecordTime()));
  }
}
