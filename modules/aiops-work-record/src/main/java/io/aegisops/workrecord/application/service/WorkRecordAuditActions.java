package io.aegisops.workrecord.application.service;

public final class WorkRecordAuditActions {
  private WorkRecordAuditActions() {}

  public static final String TEMPLATE_CREATE = "work_record.template.create";
  public static final String TEMPLATE_UPDATE = "work_record.template.update";
  public static final String TEMPLATE_DRAFT_UPDATE = "work_record.template.draft.update";
  public static final String TEMPLATE_PUBLISH = "work_record.template.publish";

  public static final String FIELD_CREATE = "work_record.template.field.create";
  public static final String FIELD_UPDATE = "work_record.template.field.update";
  public static final String FIELD_DISABLE = "work_record.template.field.disable";

  public static final String RECORD_CREATE = "work_record.record.create";
  public static final String RECORD_UPDATE = "work_record.record.update";
  public static final String RECORD_DELETE = "work_record.record.delete";
  public static final String RECORD_EXPORT = "work_record.record.export";
}
